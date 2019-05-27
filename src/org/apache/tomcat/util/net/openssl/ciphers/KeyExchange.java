package org.apache.tomcat.util.net.openssl.ciphers;

enum KeyExchange {
    EECDH /* SSL_kEECDH - 短暂的ECDH */,
    RSA   /* SSL_kRSA   - RSA key exchange */,
    DHr   /* SSL_kDHr   - DH cert, RSA CA cert */ /* 没有这样的密码套件支持! */,
    DHd   /* SSL_kDHd   - DH cert, DSA CA cert */ /* 没有这样的密码套件支持! */,
    EDH   /* SSL_kDHE   - tmp DH key no DH cert */,
    PSK   /* SSK_kPSK   - PSK */,
    FZA   /* SSL_kFZA   - Fortezza */  /* 没有这样的密码套件支持! */,
    KRB5  /* SSL_kKRB5  - Kerberos 5 key exchange */,
    ECDHr /* SSL_kECDHr - ECDH cert, RSA CA cert */,
    ECDHe /* SSL_kECDHe - ECDH cert, ECDSA CA cert */,
    GOST  /* SSL_kGOST  - GOST key exchange */,
    SRP   /* SSL_kSRP   - SRP */,
    RSAPSK,
    ECDHEPSK,
    DHEPSK;
}
