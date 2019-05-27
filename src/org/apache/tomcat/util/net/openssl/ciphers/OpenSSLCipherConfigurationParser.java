package org.apache.tomcat.util.net.openssl.ciphers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.Constants;
import org.apache.tomcat.util.res.StringManager;

/**
 * 负责解析openSSL表达式以定义密码列表的类.
 */
public class OpenSSLCipherConfigurationParser {

    private static final Log log = LogFactory.getLog(OpenSSLCipherConfigurationParser.class);
    private static final StringManager sm =
            StringManager.getManager("org.apache.tomcat.util.net.jsse.res");

    private static boolean initialized = false;

    private static final String SEPARATOR = ":|,| ";
    /**
     * 如果使用！然后该密码将从列表中永久删除. 删除的密码即使在显式声明的情况下也不会再次出现在列表中.
     */
    private static final String EXCLUDE = "!";
    /**
     * 如果使用 - 然后该密码将从列表中永久删除, 但是一些或所有的密码可以通过后面的选项再次添加.
     */
    private static final String DELETE = "-";
    /**
     * 如果使用 + 然后将密码移到列表的末尾. 这个选项不添加任何新的密码，它只是移动匹配现有的密码.
     */
    private static final String TO_END = "+";
     /**
     * 可以使用+字符将密码套件列表组合在单个密码字符串中. 用作逻辑和运算.
     * 例如，Sa1+DES表示包含Sa1和DES算法的所有密码套件.
     */
    private static final String AND = "+";
    /**
     * 所有密码通过它们的OpenSSL别名.
     */
    private static final Map<String, List<Cipher>> aliases = new LinkedHashMap<>();

    /**
     * 没有加密的 'NULL'密码. 因为它们根本不提供加密，并且是安全风险，除非明确包含，否则它们将被禁用.
     */
    private static final String eNULL = "eNULL";
    /**
     * 没有认证的密码套件. 这是目前匿名的DH算法. 这 些密码套件很容易受到“中间人”攻击，所以它们的使用通常是不允许的.
     */
    private static final String aNULL = "aNULL";

    /**
     * 'high' 加密密码套件.  这意味着具有大于128位的密钥长度的那些,  以及一些带有128位密钥的密码套件.
     */
    private static final String HIGH = "HIGH";
    /**
     * 'medium' 加密密码套件, 目前使用128位加密的一些.
     */
    private static final String MEDIUM = "MEDIUM";
    /**
     * 'low' 加密密码套件, 目前使用64位或56位加密算法但不包括出口密码套件.
     */
    private static final String LOW = "LOW";
    /**
     * 导出加密算法. 包括40位和56位算法.
     */
    private static final String EXPORT = "EXPORT";
    /**
     * 40位导出加密算法.
     */
    private static final String EXPORT40 = "EXPORT40";
    /**
     * 56位导出加密算法.
     */
    private static final String EXPORT56 = "EXPORT56";
    /**
     * 使用RSA密钥交换的密码套件.
     */
    private static final String kRSA = "kRSA";
    /**
     * 使用RSA认证的密码套件.
     */
    private static final String aRSA = "aRSA";
    /**
     * 使用RSA进行密钥交换的密码套件.
     * 不管文档怎么说, RSA相当于KRSA.
     */
    private static final String RSA = "RSA";
    /**
     * 使用短暂DH密钥协议的密码套件.
     */
    private static final String kEDH = "kEDH";
    /**
     * 使用短暂DH密钥协议的密码套件.
     */
    private static final String kDHE = "kDHE";
    /**
     * 使用短暂DH密钥协议的密码套件. 相当于 kEDH:-ADH
     */
    private static final String EDH = "EDH";
    /**
     * 使用短暂DH密钥协议的密码套件. 相当于 kEDH:-ADH
     */
    private static final String DHE = "DHE";
    /**
     * Cipher suites using DH key agreement and DH certificates signed by CAs with RSA keys.
     */
    private static final String kDHr = "kDHr";
    /**
     * Cipher suites using DH key agreement and DH certificates signed by CAs with DSS keys.
     */
    private static final String kDHd = "kDHd";
    /**
     * Cipher suites using DH key agreement and DH certificates signed by CAs with RSA or DSS keys.
     */
    private static final String kDH = "kDH";
    /**
     * Cipher suites using fixed ECDH key agreement signed by CAs with RSA keys.
     */
    private static final String kECDHr = "kECDHr";
    /**
     * Cipher suites using fixed ECDH key agreement signed by CAs with ECDSA keys.
     */
    private static final String kECDHe = "kECDHe";
    /**
     * Cipher suites using fixed ECDH key agreement signed by CAs with RSA and ECDSA keys or either respectively.
     */
    private static final String kECDH = "kECDH";
    /**
     * Cipher suites using ephemeral ECDH key agreement, including anonymous cipher suites.
     */
    private static final String kEECDH = "kEECDH";
    /**
     * Cipher suites using ephemeral ECDH key agreement, excluding anonymous cipher suites.
     * Same as "kEECDH:-AECDH"
     */
    private static final String EECDH = "EECDH";
    /**
     * Cipher suitesusing ECDH key exchange, including anonymous, ephemeral and fixed ECDH.
     */
    private static final String ECDH = "ECDH";
    /**
     * Cipher suites using ephemeral ECDH key agreement, including anonymous cipher suites.
     */
    private static final String kECDHE = "kECDHE";
    /**
     * Cipher suites using authenticated ephemeral ECDH key agreement
     */
    private static final String ECDHE = "ECDHE";
    /**
     * Cipher suites using authenticated ephemeral ECDH key agreement
     */
    private static final String EECDHE = "EECDHE";
    /**
     * Anonymous Elliptic Curve Diffie Hellman cipher suites.
     */
    private static final String AECDH = "AECDH";
    /**
     * Cipher suites using DSS for key exchange
     */
    private static final String DSS = "DSS";
    /**
     * Cipher suites using DSS authentication, i.e. the certificates carry DSS keys.
     */
    private static final String aDSS = "aDSS";
    /**
     * Cipher suites effectively using DH authentication, i.e. the certificates carry DH keys.
     */
    private static final String aDH = "aDH";
    /**
     * Cipher suites effectively using ECDH authentication, i.e. the certificates carry ECDH keys.
     */
    private static final String aECDH = "aECDH";
    /**
     * Cipher suites effectively using ECDSA authentication, i.e. the certificates carry ECDSA keys.
     */
    private static final String aECDSA = "aECDSA";
    /**
     * Cipher suites effectively using ECDSA authentication, i.e. the certificates carry ECDSA keys.
     */
    private static final String ECDSA = "ECDSA";
    /**
     * Ciphers suites using FORTEZZA key exchange algorithms.
     */
    private static final String kFZA = "kFZA";
    /**
     * Ciphers suites using FORTEZZA authentication algorithms.
     */
    private static final String aFZA = "aFZA";
    /**
     * Ciphers suites using FORTEZZA encryption algorithms.
     */
    private static final String eFZA = "eFZA";
    /**
     * Ciphers suites using all FORTEZZA algorithms.
     */
    private static final String FZA = "FZA";
    /**
     * Cipher suites using DH, including anonymous DH, ephemeral DH and fixed DH.
     */
    private static final String DH = "DH";
    /**
     * Anonymous DH cipher suites.
     */
    private static final String ADH = "ADH";
    /**
     * Cipher suites using 128 bit AES.
     */
    private static final String AES128 = "AES128";
    /**
     * Cipher suites using 256 bit AES.
     */
    private static final String AES256 = "AES256";
    /**
     * Cipher suites using either 128 or 256 bit AES.
     */
    private static final String AES = "AES";
    /**
     * AES in Galois Counter Mode (GCM): these cipher suites are only supported in TLS v1.2.
     */
    private static final String AESGCM = "AESGCM";
    /**
     * AES in Counter with CBC-MAC Mode (CCM).
     */
    private static final String AESCCM = "AESCCM";
    /**
     * AES in Counter with CBC-MAC Mode and 8-byte authentication (CCM8).
     */
    private static final String AESCCM8 = "AESCCM8";
    /**
     * Cipher suites using 128 bit CAMELLIA.
     */
    private static final String CAMELLIA128 = "CAMELLIA128";
    /**
     * Cipher suites using 256 bit CAMELLIA.
     */
    private static final String CAMELLIA256 = "CAMELLIA256";
    /**
     * Cipher suites using either 128 or 256 bit CAMELLIA.
     */
    private static final String CAMELLIA = "CAMELLIA";
    /**
     * Cipher suites using CHACHA20.
     */
    private static final String CHACHA20 = "CHACHA20";
    /**
     * Cipher suites using triple DES.
     */
    private static final String TRIPLE_DES = "3DES";
    /**
     * Cipher suites using DES (not triple DES).
     */
    private static final String DES = "DES";
    /**
     * Cipher suites using RC4.
     */
    private static final String RC4 = "RC4";
    /**
     * Cipher suites using RC2.
     */
    private static final String RC2 = "RC2";
    /**
     * Cipher suites using IDEA.
     */
    private static final String IDEA = "IDEA";
    /**
     * Cipher suites using SEED.
     */
    private static final String SEED = "SEED";
    /**
     * Cipher suites using MD5.
     */
    private static final String MD5 = "MD5";
    /**
     * Cipher suites using SHA1.
     */
    private static final String SHA1 = "SHA1";
    /**
     * Cipher suites using SHA1.
     */
    private static final String SHA = "SHA";
    /**
     * Cipher suites using SHA256.
     */
    private static final String SHA256 = "SHA256";
    /**
     * Cipher suites using SHA384.
     */
    private static final String SHA384 = "SHA384";
    /**
     * Cipher suites using KRB5.
     */
    private static final String KRB5 = "KRB5";
    /**
     * Cipher suites using GOST R 34.10 (either 2001 or 94) for authentication.
     */
    private static final String aGOST = "aGOST";
    /**
     * Cipher suites using GOST R 34.10-2001 for authentication.
     */
    private static final String aGOST01 = "aGOST01";
    /**
     * Cipher suites using GOST R 34.10-94 authentication (note that R 34.10-94 standard has been expired so use GOST R
     * 34.10-2001)
     */
    private static final String aGOST94 = "aGOST94";
    /**
     * Cipher suites using using VKO 34.10 key exchange, specified in the RFC 4357.
     */
    private static final String kGOST = "kGOST";
    /**
     * Cipher suites, using HMAC based on GOST R 34.11-94.
     */
    private static final String GOST94 = "GOST94";
    /**
     * Cipher suites using GOST 28147-89 MAC instead of HMAC.
     */
    private static final String GOST89MAC = "GOST89MAC";
    /**
     * Cipher suites using SRP authentication, specified in the RFC 5054.
     */
    private static final String aSRP = "aSRP";
    /**
     * Cipher suites using SRP key exchange, specified in the RFC 5054.
     */
    private static final String kSRP = "kSRP";
    /**
     * Same as kSRP
     */
    private static final String SRP = "SRP";
    /**
     * Cipher suites using pre-shared keys (PSK).
     */
    private static final String PSK = "PSK";
    /**
     * Cipher suites using PSK authentication.
     */
    private static final String aPSK = "aPSK";
    /**
     * Cipher suites using PSK key 'exchange'.
     */
    private static final String kPSK = "kPSK";
    private static final String kRSAPSK = "kRSAPSK";
    private static final String kECDHEPSK = "kECDHEPSK";
    private static final String kDHEPSK = "kDHEPSK";

    private static final String DEFAULT = "DEFAULT";
    private static final String COMPLEMENTOFDEFAULT = "COMPLEMENTOFDEFAULT";

    private static final String ALL = "ALL";
    private static final String COMPLEMENTOFALL = "COMPLEMENTOFALL";

    private static final Map<String,String> jsseToOpenSSL = new HashMap<>();

    private static final void init() {

        for (Cipher cipher : Cipher.values()) {
            String alias = cipher.getOpenSSLAlias();
            if (aliases.containsKey(alias)) {
                aliases.get(alias).add(cipher);
            } else {
                List<Cipher> list = new ArrayList<>();
                list.add(cipher);
                aliases.put(alias, list);
            }
            aliases.put(cipher.name(), Collections.singletonList(cipher));

            for (String openSSlAltName : cipher.getOpenSSLAltNames()) {
                if (aliases.containsKey(openSSlAltName)) {
                    aliases.get(openSSlAltName).add(cipher);
                } else {
                    List<Cipher> list = new ArrayList<>();
                    list.add(cipher);
                    aliases.put(openSSlAltName, list);
                }

            }

            jsseToOpenSSL.put(cipher.name(), cipher.getOpenSSLAlias());
            Set<String> jsseNames = cipher.getJsseNames();
            for (String jsseName : jsseNames) {
                jsseToOpenSSL.put(jsseName, cipher.getOpenSSLAlias());
            }
        }
        List<Cipher> allCiphersList = Arrays.asList(Cipher.values());
        Collections.reverse(allCiphersList);
        LinkedHashSet<Cipher> allCiphers = defaultSort(new LinkedHashSet<>(allCiphersList));
        addListAlias(eNULL, filterByEncryption(allCiphers, Collections.singleton(Encryption.eNULL)));
        LinkedHashSet<Cipher> all = new LinkedHashSet<>(allCiphers);
        remove(all, eNULL);
        addListAlias(ALL, all);
        addListAlias(HIGH, filterByEncryptionLevel(allCiphers, Collections.singleton(EncryptionLevel.HIGH)));
        addListAlias(MEDIUM, filterByEncryptionLevel(allCiphers, Collections.singleton(EncryptionLevel.MEDIUM)));
        addListAlias(LOW, filterByEncryptionLevel(allCiphers, Collections.singleton(EncryptionLevel.LOW)));
        addListAlias(EXPORT, filterByEncryptionLevel(allCiphers, new HashSet<>(Arrays.asList(EncryptionLevel.EXP40, EncryptionLevel.EXP56))));
        aliases.put("EXP", aliases.get(EXPORT));
        addListAlias(EXPORT40, filterByEncryptionLevel(allCiphers, Collections.singleton(EncryptionLevel.EXP40)));
        addListAlias(EXPORT56, filterByEncryptionLevel(allCiphers, Collections.singleton(EncryptionLevel.EXP56)));
        aliases.put("NULL", aliases.get(eNULL));
        aliases.put(COMPLEMENTOFALL, aliases.get(eNULL));
        addListAlias(aNULL, filterByAuthentication(allCiphers, Collections.singleton(Authentication.aNULL)));
        addListAlias(kRSA, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.RSA)));
        addListAlias(aRSA, filterByAuthentication(allCiphers, Collections.singleton(Authentication.RSA)));
        // 无论文档怎么说, RSA 相当于 kRSA
        aliases.put(RSA, aliases.get(kRSA));
        addListAlias(kEDH, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.EDH)));
        addListAlias(kDHE, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.EDH)));
        Set<Cipher> edh = filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.EDH));
        edh.removeAll(filterByAuthentication(allCiphers, Collections.singleton(Authentication.aNULL)));
        addListAlias(EDH, edh);
        addListAlias(DHE, edh);
        addListAlias(kDHr, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.DHr)));
        addListAlias(kDHd, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.DHd)));
        addListAlias(kDH, filterByKeyExchange(allCiphers, new HashSet<>(Arrays.asList(KeyExchange.DHr, KeyExchange.DHd))));

        addListAlias(kECDHr, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.ECDHr)));
        addListAlias(kECDHe, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.ECDHe)));
        addListAlias(kECDH, filterByKeyExchange(allCiphers, new HashSet<>(Arrays.asList(KeyExchange.ECDHe, KeyExchange.ECDHr))));
        addListAlias(ECDH, filterByKeyExchange(allCiphers, new HashSet<>(Arrays.asList(KeyExchange.ECDHe, KeyExchange.ECDHr, KeyExchange.EECDH))));
        addListAlias(kECDHE, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.EECDH)));

        Set<Cipher> ecdhe = filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.EECDH));
        remove(ecdhe, aNULL);
        addListAlias(ECDHE, ecdhe);

        addListAlias(kEECDH, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.EECDH)));
        aliases.put(EECDHE, aliases.get(kEECDH));
        Set<Cipher> eecdh = filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.EECDH));
        eecdh.removeAll(filterByAuthentication(allCiphers, Collections.singleton(Authentication.aNULL)));
        addListAlias(EECDH, eecdh);
        addListAlias(aDSS, filterByAuthentication(allCiphers, Collections.singleton(Authentication.DSS)));
        aliases.put(DSS, aliases.get(aDSS));
        addListAlias(aDH, filterByAuthentication(allCiphers, Collections.singleton(Authentication.DH)));
        Set<Cipher> aecdh = filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.EECDH));
        addListAlias(AECDH, filterByAuthentication(aecdh, Collections.singleton(Authentication.aNULL)));
        addListAlias(aECDH, filterByAuthentication(allCiphers, Collections.singleton(Authentication.ECDH)));
        addListAlias(ECDSA, filterByAuthentication(allCiphers, Collections.singleton(Authentication.ECDSA)));
        aliases.put(aECDSA, aliases.get(ECDSA));
        addListAlias(kFZA, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.FZA)));
        addListAlias(aFZA, filterByAuthentication(allCiphers, Collections.singleton(Authentication.FZA)));
        addListAlias(eFZA, filterByEncryption(allCiphers, Collections.singleton(Encryption.FZA)));
        addListAlias(FZA, filter(allCiphers, null, Collections.singleton(KeyExchange.FZA), Collections.singleton(Authentication.FZA), Collections.singleton(Encryption.FZA), null, null));
        addListAlias(Constants.SSL_PROTO_TLSv1_2, filterByProtocol(allCiphers, Collections.singleton(Protocol.TLSv1_2)));
        addListAlias(Constants.SSL_PROTO_TLSv1_0, filterByProtocol(allCiphers, Collections.singleton(Protocol.TLSv1)));
        addListAlias(Constants.SSL_PROTO_SSLv3, filterByProtocol(allCiphers, Collections.singleton(Protocol.SSLv3)));
        aliases.put(Constants.SSL_PROTO_TLSv1, aliases.get(Constants.SSL_PROTO_TLSv1_0));
        addListAlias(Constants.SSL_PROTO_SSLv2, filterByProtocol(allCiphers, Collections.singleton(Protocol.SSLv2)));
        addListAlias(DH, filterByKeyExchange(allCiphers, new HashSet<>(Arrays.asList(KeyExchange.DHr, KeyExchange.DHd, KeyExchange.EDH))));
        Set<Cipher> adh = filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.EDH));
        adh.retainAll(filterByAuthentication(allCiphers, Collections.singleton(Authentication.aNULL)));
        addListAlias(ADH, adh);
        addListAlias(AES128, filterByEncryption(allCiphers, new HashSet<>(Arrays.asList(Encryption.AES128, Encryption.AES128CCM, Encryption.AES128CCM8, Encryption.AES128GCM))));
        addListAlias(AES256, filterByEncryption(allCiphers, new HashSet<>(Arrays.asList(Encryption.AES256, Encryption.AES256CCM, Encryption.AES256CCM8, Encryption.AES256GCM))));
        addListAlias(AES, filterByEncryption(allCiphers, new HashSet<>(Arrays.asList(Encryption.AES128, Encryption.AES128CCM, Encryption.AES128CCM8, Encryption.AES128GCM, Encryption.AES256, Encryption.AES256CCM, Encryption.AES256CCM8, Encryption.AES256GCM))));
        addListAlias(AESGCM, filterByEncryption(allCiphers, new HashSet<>(Arrays.asList(Encryption.AES128GCM, Encryption.AES256GCM))));
        addListAlias(AESCCM, filterByEncryption(allCiphers, new HashSet<>(Arrays.asList(Encryption.AES128CCM, Encryption.AES128CCM8, Encryption.AES256CCM, Encryption.AES256CCM8))));
        addListAlias(AESCCM8, filterByEncryption(allCiphers, new HashSet<>(Arrays.asList(Encryption.AES128CCM8, Encryption.AES256CCM8))));
        addListAlias(CAMELLIA, filterByEncryption(allCiphers, new HashSet<>(Arrays.asList(Encryption.CAMELLIA128, Encryption.CAMELLIA256))));
        addListAlias(CAMELLIA128, filterByEncryption(allCiphers, Collections.singleton(Encryption.CAMELLIA128)));
        addListAlias(CAMELLIA256, filterByEncryption(allCiphers, Collections.singleton(Encryption.CAMELLIA256)));
        addListAlias(CHACHA20, filterByEncryption(allCiphers, Collections.singleton(Encryption.CHACHA20POLY1305)));
        addListAlias(TRIPLE_DES, filterByEncryption(allCiphers, Collections.singleton(Encryption.TRIPLE_DES)));
        addListAlias(DES, filterByEncryption(allCiphers, Collections.singleton(Encryption.DES)));
        addListAlias(RC4, filterByEncryption(allCiphers, Collections.singleton(Encryption.RC4)));
        addListAlias(RC2, filterByEncryption(allCiphers, Collections.singleton(Encryption.RC2)));
        addListAlias(IDEA, filterByEncryption(allCiphers, Collections.singleton(Encryption.IDEA)));
        addListAlias(SEED, filterByEncryption(allCiphers, Collections.singleton(Encryption.SEED)));
        addListAlias(MD5, filterByMessageDigest(allCiphers, Collections.singleton(MessageDigest.MD5)));
        addListAlias(SHA1, filterByMessageDigest(allCiphers, Collections.singleton(MessageDigest.SHA1)));
        aliases.put(SHA, aliases.get(SHA1));
        addListAlias(SHA256, filterByMessageDigest(allCiphers, Collections.singleton(MessageDigest.SHA256)));
        addListAlias(SHA384, filterByMessageDigest(allCiphers, Collections.singleton(MessageDigest.SHA384)));
        addListAlias(aGOST, filterByAuthentication(allCiphers, new HashSet<>(Arrays.asList(Authentication.GOST01, Authentication.GOST94))));
        addListAlias(aGOST01, filterByAuthentication(allCiphers, Collections.singleton(Authentication.GOST01)));
        addListAlias(aGOST94, filterByAuthentication(allCiphers, Collections.singleton(Authentication.GOST94)));
        addListAlias(kGOST, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.GOST)));
        addListAlias(GOST94, filterByMessageDigest(allCiphers, Collections.singleton(MessageDigest.GOST94)));
        addListAlias(GOST89MAC, filterByMessageDigest(allCiphers, Collections.singleton(MessageDigest.GOST89MAC)));
        addListAlias(PSK, filter(allCiphers, null, new HashSet<>(Arrays.asList(KeyExchange.PSK, KeyExchange.RSAPSK, KeyExchange.DHEPSK, KeyExchange.ECDHEPSK)), Collections.singleton(Authentication.PSK), null, null, null));
        addListAlias(aPSK, filterByAuthentication(allCiphers, Collections.singleton(Authentication.PSK)));
        addListAlias(kPSK, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.PSK)));
        addListAlias(kRSAPSK, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.RSAPSK)));
        addListAlias(kECDHEPSK, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.ECDHEPSK)));
        addListAlias(kDHEPSK, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.DHEPSK)));
        addListAlias(KRB5, filter(allCiphers, null, Collections.singleton(KeyExchange.KRB5), Collections.singleton(Authentication.KRB5), null, null, null));
        addListAlias(aSRP, filterByAuthentication(allCiphers, Collections.singleton(Authentication.SRP)));
        addListAlias(kSRP, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.SRP)));
        addListAlias(SRP, filterByKeyExchange(allCiphers, Collections.singleton(KeyExchange.SRP)));
        initialized = true;
        // 无论OpenSSL文档说什么, 默认也不包括SSLV2
        addListAlias(DEFAULT, parse("ALL:!EXPORT:!eNULL:!aNULL:!SSLv2:!DES:!RC2:!RC4:!DSS:!SEED:!IDEA:!CAMELLIA:!AESCCM:!3DES"));
        // COMPLEMENTOFDEFAULT 也不完全由文档定义
        LinkedHashSet<Cipher> complementOfDefault = filterByKeyExchange(all, new HashSet<>(Arrays.asList(KeyExchange.EDH,KeyExchange.EECDH)));
        complementOfDefault = filterByAuthentication(complementOfDefault, Collections.singleton(Authentication.aNULL));
        complementOfDefault.removeAll(aliases.get(eNULL));
        complementOfDefault.addAll(aliases.get(Constants.SSL_PROTO_SSLv2));
        complementOfDefault.addAll(aliases.get(EXPORT));
        complementOfDefault.addAll(aliases.get(DES));
        complementOfDefault.addAll(aliases.get(TRIPLE_DES));
        complementOfDefault.addAll(aliases.get(RC2));
        complementOfDefault.addAll(aliases.get(RC4));
        complementOfDefault.addAll(aliases.get(aDSS));
        complementOfDefault.addAll(aliases.get(SEED));
        complementOfDefault.addAll(aliases.get(IDEA));
        complementOfDefault.addAll(aliases.get(CAMELLIA));
        complementOfDefault.addAll(aliases.get(AESCCM));
        defaultSort(complementOfDefault);
        addListAlias(COMPLEMENTOFDEFAULT, complementOfDefault);
    }

    static void addListAlias(String alias, Set<Cipher> ciphers) {
        aliases.put(alias, new ArrayList<>(ciphers));
    }

    static void moveToEnd(final LinkedHashSet<Cipher> ciphers, final String alias) {
        moveToEnd(ciphers, aliases.get(alias));
    }

    static void moveToEnd(final LinkedHashSet<Cipher> ciphers, final Collection<Cipher> toBeMovedCiphers) {
        List<Cipher> movedCiphers = new ArrayList<>(toBeMovedCiphers);
        movedCiphers.retainAll(ciphers);
        ciphers.removeAll(movedCiphers);
        ciphers.addAll(movedCiphers);
    }

    static void moveToStart(final LinkedHashSet<Cipher> ciphers, final Collection<Cipher> toBeMovedCiphers) {
        List<Cipher> movedCiphers = new ArrayList<>(toBeMovedCiphers);
        List<Cipher> originalCiphers = new ArrayList<>(ciphers);
        movedCiphers.retainAll(ciphers);
        ciphers.clear();
        ciphers.addAll(movedCiphers);
        ciphers.addAll(originalCiphers);
    }

    static void add(final LinkedHashSet<Cipher> ciphers, final String alias) {
        ciphers.addAll(aliases.get(alias));
    }

    static void remove(final Set<Cipher> ciphers, final String alias) {
        ciphers.removeAll(aliases.get(alias));
    }

    static LinkedHashSet<Cipher> strengthSort(final LinkedHashSet<Cipher> ciphers) {
        /*
         * 这个例程对密码的降序强度进行排序. 排序必须保持排序前的顺序, 因此使用正常排序例程, 因为 '+' 移动到列表的末尾.
         */
        Set<Integer> keySizes = new HashSet<>();
        for (Cipher cipher : ciphers) {
            keySizes.add(Integer.valueOf(cipher.getStrength_bits()));
        }
        List<Integer> strength_bits = new ArrayList<>(keySizes);
        Collections.sort(strength_bits);
        Collections.reverse(strength_bits);
        final LinkedHashSet<Cipher> result = new LinkedHashSet<>(ciphers);
        for (int strength : strength_bits) {
            moveToEnd(result, filterByStrengthBits(ciphers, strength));
        }
        return result;
    }

    /*
     * See
     * https://github.com/openssl/openssl/blob/7c96dbcdab959fef74c4caae63cdebaa354ab252/ssl/ssl_ciph.c#L1371
     */
    static LinkedHashSet<Cipher> defaultSort(final LinkedHashSet<Cipher> ciphers) {
        final LinkedHashSet<Cipher> result = new LinkedHashSet<>(ciphers.size());
        final LinkedHashSet<Cipher> ecdh = new LinkedHashSet<>(ciphers.size());

        /* 其他一切都是平等的, 比起其他关键交换机制更喜欢短期ECDH */
        ecdh.addAll(filterByKeyExchange(ciphers, Collections.singleton(KeyExchange.EECDH)));

        /* AES是首选的对称密码 */
        Set<Encryption> aes = new HashSet<>(Arrays.asList(Encryption.AES128, Encryption.AES128CCM,
                Encryption.AES128CCM8, Encryption.AES128GCM, Encryption.AES256,
                Encryption.AES256CCM, Encryption.AES256CCM8, Encryption.AES256GCM));

        /* 现在按优先顺序排列所有密码: */
        result.addAll(filterByEncryption(ecdh, aes));
        result.addAll(filterByEncryption(ciphers, aes));

        /* 添加其他一切 */
        result.addAll(ecdh);
        result.addAll(ciphers);

        /* MD5的低优先级 */
        moveToEnd(result, filterByMessageDigest(result, Collections.singleton(MessageDigest.MD5)));

        /* 将匿名密码移动到末尾. 通常, 这些仍然禁用.
         * (对于允许它们的应用程序, 它们并不太坏, 但是更喜欢认证密码.) */
        moveToEnd(result, filterByAuthentication(result, Collections.singleton(Authentication.aNULL)));

        /* 移动密码没有前向保密 */
        moveToEnd(result, filterByAuthentication(result, Collections.singleton(Authentication.ECDH)));
        moveToEnd(result, filterByKeyExchange(result, Collections.singleton(KeyExchange.RSA)));
        moveToEnd(result, filterByKeyExchange(result, Collections.singleton(KeyExchange.PSK)));

        /* RC4 is sort-of broken -- move the the end */
        moveToEnd(result, filterByEncryption(result, Collections.singleton(Encryption.RC4)));
        return strengthSort(result);
    }

    static Set<Cipher> filterByStrengthBits(Set<Cipher> ciphers, int strength_bits) {
        Set<Cipher> result = new LinkedHashSet<>(ciphers.size());
        for (Cipher cipher : ciphers) {
            if (cipher.getStrength_bits() == strength_bits) {
                result.add(cipher);
            }
        }
        return result;
    }

    static Set<Cipher> filterByProtocol(Set<Cipher> ciphers, Set<Protocol> protocol) {
        return filter(ciphers, protocol, null, null, null, null, null);
    }

    static LinkedHashSet<Cipher> filterByKeyExchange(Set<Cipher> ciphers, Set<KeyExchange> kx) {
        return filter(ciphers, null, kx, null, null, null, null);
    }

    static LinkedHashSet<Cipher> filterByAuthentication(Set<Cipher> ciphers, Set<Authentication> au) {
        return filter(ciphers, null, null, au, null, null, null);
    }

    static Set<Cipher> filterByEncryption(Set<Cipher> ciphers, Set<Encryption> enc) {
        return filter(ciphers, null, null, null, enc, null, null);
    }

    static Set<Cipher> filterByEncryptionLevel(Set<Cipher> ciphers, Set<EncryptionLevel> level) {
        return filter(ciphers, null, null, null, null, level, null);
    }

    static Set<Cipher> filterByMessageDigest(Set<Cipher> ciphers, Set<MessageDigest> mac) {
        return filter(ciphers, null, null, null, null, null, mac);
    }

    static LinkedHashSet<Cipher> filter(Set<Cipher> ciphers, Set<Protocol> protocol, Set<KeyExchange> kx,
            Set<Authentication> au, Set<Encryption> enc, Set<EncryptionLevel> level, Set<MessageDigest> mac) {
        LinkedHashSet<Cipher> result = new LinkedHashSet<>(ciphers.size());
        for (Cipher cipher : ciphers) {
            if (protocol != null && protocol.contains(cipher.getProtocol())) {
                result.add(cipher);
            }
            if (kx != null && kx.contains(cipher.getKx())) {
                result.add(cipher);
            }
            if (au != null && au.contains(cipher.getAu())) {
                result.add(cipher);
            }
            if (enc != null && enc.contains(cipher.getEnc())) {
                result.add(cipher);
            }
            if (level != null && level.contains(cipher.getLevel())) {
                result.add(cipher);
            }
            if (mac != null && mac.contains(cipher.getMac())) {
                result.add(cipher);
            }
        }
        return result;
    }

    public static LinkedHashSet<Cipher> parse(String expression) {
        if (!initialized) {
            init();
        }
        String[] elements = expression.split(SEPARATOR);
        LinkedHashSet<Cipher> ciphers = new LinkedHashSet<>();
        Set<Cipher> removedCiphers = new HashSet<>();
        for (String element : elements) {
            if (element.startsWith(DELETE)) {
                String alias = element.substring(1);
                if (aliases.containsKey(alias)) {
                    remove(ciphers, alias);
                }
            } else if (element.startsWith(EXCLUDE)) {
                String alias = element.substring(1);
                if (aliases.containsKey(alias)) {
                    removedCiphers.addAll(aliases.get(alias));
                } else {
                    log.warn(sm.getString("jsse.openssl.unknownElement", alias));
                }
            } else if (element.startsWith(TO_END)) {
                String alias = element.substring(1);
                if (aliases.containsKey(alias)) {
                    moveToEnd(ciphers, alias);
                }
            } else if ("@STRENGTH".equals(element)) {
                strengthSort(ciphers);
                break;
            } else if (aliases.containsKey(element)) {
                add(ciphers, element);
            } else if (element.contains(AND)) {
                String[] intersections = element.split("\\" + AND);
                if(intersections.length > 0 && aliases.containsKey(intersections[0])) {
                    List<Cipher> result = new ArrayList<>(aliases.get(intersections[0]));
                    for(int i = 1; i < intersections.length; i++) {
                        if(aliases.containsKey(intersections[i])) {
                            result.retainAll(aliases.get(intersections[i]));
                        }
                    }
                     ciphers.addAll(result);
                }
            }
        }
        ciphers.removeAll(removedCiphers);
        return ciphers;
    }

    public static List<String> convertForJSSE(Collection<Cipher> ciphers) {
        List<String> result = new ArrayList<>(ciphers.size());
        for (Cipher cipher : ciphers) {
            result.addAll(cipher.getJsseNames());
        }
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("jsse.openssl.effectiveCiphers", displayResult(ciphers, true, ",")));
        }
        return result;
    }

    /**
     * 根据OpenSSL语法解析指定表达式，并返回标准JSSE密码名称的列表.
     *
     * @param expression 定义密码列表的OpenSSL表达式.
     * 
     * @return 相应的密码列表.
     */
    public static List<String> parseExpression(String expression) {
        return convertForJSSE(parse(expression));
    }


    /**
     * 将JSSE密码名称转换为OpenSSL密码名称.
     *
     * @param jsseCipherName 密码的JSSE名称
     *
     * @return 指定的JSSE密码的OpenSSL名称
     */
    public static String jsseToOpenSSL(String jsseCipherName) {
        if (!initialized) {
            init();
        }
        return jsseToOpenSSL.get(jsseCipherName);
    }


    /**
     * 将OpenSSL密码名称转换为JSSE密码名称.
     *
     * @param opensslCipherName 密码的OpenSSL名称
     *
     * @return 指定的OpenSSL密码的JSSE名称. 如果没有人知道, 将返回IANA标准名称
     */
    public static String openSSLToJsse(String opensslCipherName) {
        if (!initialized) {
            init();
        }
        List<Cipher> ciphers = aliases.get(opensslCipherName);
        if (ciphers == null || ciphers.size() != 1) {
            // 不是OpenSSL密码名称
            return null;
        }
        Cipher cipher = ciphers.get(0);
        // 每个密码总是至少有一个JSSE名称
        return cipher.getJsseNames().iterator().next();
    }


    static String displayResult(Collection<Cipher> ciphers, boolean useJSSEFormat, String separator) {
        if (ciphers.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(ciphers.size() * 16);
        for (Cipher cipher : ciphers) {
            if (useJSSEFormat) {
                for (String name : cipher.getJsseNames()) {
                    builder.append(name);
                    builder.append(separator);
                }
            } else {
                builder.append(cipher.getOpenSSLAlias());
            }
            builder.append(separator);
        }
        return builder.toString().substring(0, builder.length() - 1);
    }
}
