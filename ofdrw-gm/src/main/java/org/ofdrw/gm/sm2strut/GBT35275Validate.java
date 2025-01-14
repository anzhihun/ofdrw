package org.ofdrw.gm.sm2strut;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.jcajce.provider.digest.SM3;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Hex;
import org.ofdrw.gm.cert.CertTools;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Arrays;

/**
 * 根据 GM/T 0099-2020 7.2.2 数据格式要求
 * <p>
 * b) 签名类型为数字签名且签名算法使用SM2时，签名值数据应遵循 GB/T 35275
 * <p>
 * 数字签名验证容器
 *
 * @author 权观宇
 * @since 2021-8-9 16:15:11
 */
public class GBT35275Validate {


    /**
     * 验证 GBT35275 SignedData数据
     *
     * @param alg         算法
     * @param tbsContent  待签名数据原文，不需要提前计算摘要
     * @param signedValue 签名值DER编码
     * @return 验证结果
     * @throws GeneralSecurityException 签名计算法错误
     */
    public static VerifyInfo validate(String alg, byte[] tbsContent, byte[] signedValue)
            throws GeneralSecurityException {
        ContentInfo contentInfo = ContentInfo.getInstance(signedValue);
        if (contentInfo == null) {
            throw new IllegalArgumentException("无法解析ContentInfo结构");
        }
        if (!OIDs.signedData.equals(contentInfo.getContentType())) {
            throw new IllegalArgumentException("非法的签名数据类型，类型：" + contentInfo.getContentType());
        }

        SignedData signedData = SignedData.getInstance(contentInfo.getContent());
        if (signedData == null) {
            throw new IllegalArgumentException("无法解析签名值格式，不符 GBT35275");
        }
        // 计算原文摘要
        MessageDigest md = new SM3.Digest();
        // a) 根据签名文件中的签名方案，调用杂凑算法计算签名文件的杂凑值。
        byte[] plaintextAct = md.digest(tbsContent);
        byte[] plaintext = null;
        System.out.println(Hex.toHexString(plaintextAct));

        final ASN1Encodable dataContent = signedData.getContentInfo().getContent();
        if (dataContent == null) {
//            throw new IllegalArgumentException("GBT35275 杂凑值为空");
            plaintext = plaintextAct;
        } else {
            plaintext = DEROctetString.getInstance(dataContent).getOctets();
            if (!Arrays.equals(plaintextAct, plaintext)) {
                try {
                    // [兼容非规范格式] 尝试通过Base64解码后比对
                    final byte[] decode = Base64.decode(new String(plaintext));
                    if (!Arrays.equals(plaintextAct, decode)) {
                        return VerifyInfo.Err("待签名原文不符");
                    }
                } catch (Exception e) {
                    return VerifyInfo.Err("待签名原文不符");
                }
            }
        }


        // b) 根据签名文件的签名方案，结合步骤 a) 所得的杂凑值进行签名验证。
        for (ASN1Encodable item : signedData.getSignerInfos()) {
            final SignerInfo signerInfo = SignerInfo.getInstance(item);
            IssuerAndSerialNumber iaSn = signerInfo.getIssuerAngSerialNumber();
            // 根据提供者信息找到证书
            final Certificate c = signedData.getSignCert(iaSn);
            if (c == null) {
                return VerifyInfo.Err("没有找到匹配的证书无法验证签名");
            }
            final java.security.cert.Certificate cert = CertTools.obj(c);
            Signature sg = Signature.getInstance(alg, new BouncyCastleProvider());
            sg.initVerify(cert.getPublicKey());
            sg.update(plaintext);
            byte[] signature = signerInfo.getEncryptedDigest().getOctets();
            if (!sg.verify(signature)) {
                return VerifyInfo.Err("签名值不一致");
            }
        }
        return VerifyInfo.OK();
    }
}
