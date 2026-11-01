package com.xenon.qldapm.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

public final class SePaySignature {
    private SePaySignature() {}
    public static boolean valid(String secret,String timestamp,String signature,byte[] raw,Instant now) {
        try {
            long t=Long.parseLong(timestamp);
            if(t<now.getEpochSecond()-300 || t>now.getEpochSecond()+300 || signature==null
                    || !signature.matches("sha256=[a-fA-F0-9]{64}")) return false;
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            mac.update((timestamp+".").getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(mac.doFinal(raw),HexFormat.of().parseHex(signature.substring(7)));
        } catch(Exception e){return false;}
    }
}
