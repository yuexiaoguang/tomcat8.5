package org.apache.tomcat.util.net.openssl.ciphers;

public enum Authentication {
    RSA /* RSA auth */,
    DSS /* DSS auth */,
    aNULL /* no auth (i.e. use ADH or AECDH) */,
    DH /* Fixed DH auth (kDHd or kDHr) */,
    ECDH /* Fixed ECDH auth (kECDHe or kECDHr) */,
    KRB5 /* KRB5 auth */,
    ECDSA/* ECDSA auth*/,
    PSK /* PSK auth */,
    GOST94 /* GOST R 34.10-94 signature auth */,
    GOST01 /* GOST R 34.10-2001 */,
    FZA /* Fortezza */,
    SRP
}
