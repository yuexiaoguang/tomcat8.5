package org.apache.tomcat.util.net.openssl.ciphers;

enum Encryption {
    AES128,
    AES128CCM,
    AES128CCM8,
    AES128GCM,
    AES256,
    AES256CCM,
    AES256CCM8,
    AES256GCM,
    CAMELLIA256,
    CAMELLIA128,
    CHACHA20POLY1305,
    TRIPLE_DES,
    DES,
    IDEA,
    eGOST2814789CNT,
    SEED,
    FZA,
    RC4,
    RC2,
    eNULL;
}
