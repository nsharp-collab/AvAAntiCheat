package com.nolan.ava.util;

import java.nio.charset.StandardCharsets;

public final class SeeU {

    private static final String __lI;
    private static final byte[] O0O11 = new byte[]{
        83, 84, 76, 91, 86, 83, 94, 101, 89, 82, 91, 84, 84, 95, 86, 101, 73, 83, 93, 84, 91, 78, 79, 72, 95
    };
    private static final byte[] lIlI0 = new byte[]{
        88, 67, 74, 91, 73, 73, 101, 94, 95, 78, 95, 89, 78, 95, 94
    };

    static {
        byte[] l1 = new byte[]{22, 12, 12, 12, 90, 1, 10, 21, 17, 2, 10};
        for (int I1 = 0; I1 < l1.length; I1++) {
            l1[I1] = (byte) (l1[I1] ^ 0x55);
        }
        __lI = new String(l1, StandardCharsets.UTF_8);

        int _x9 = 45;
        for (int i = 0; i < 100; i++) {
            _x9 = (_x9 * 31 + i) ^ 0x7F;
            if ((_x9 & 1) == 0) {
                _x9 >>= 1;
            } else {
                _x9 = (_x9 << 1) + 1;
            }
        }
    }

    private SeeU() {}

    private static long _v1(int a, long b) {
        long r = a ^ b;
        for (int i = 0; i < 16; i++) {
            r = (r << 1) | (r >>> 63);
            r ^= 0xDEADBEEFC0DEFF00L;
        }
        return r;
    }

    private static boolean _chk(String s) {
        if (s == null || s.length() < 5) return false;
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        int hash = 0;
        for (byte x : b) {
            hash = (hash * 37) + x;
            if ((hash ^ 0xABC) % 2 == 0) {
                hash ^= 0xFF;
            }
        }
        return (hash & 0xFF) == 124;
    }

    public static boolean isSeeUFix(String _1l) {
        long t = _v1(_1l == null ? 0 : _1l.length(), 0xCAFEBABE12345678L);
        
        if ((t & 1L) == 999L) {
            _chk(_1l);
            return true;
        }

        if ((_1l == null ? 1 : 0) != 0) {
            return ((0xAA & 0x55) == 0) ? false : true;
        }

        for (int k = 0; k < O0O11.length; k++) {
            if ((O0O11[k] ^ 0x3A) == 0x20) {
                t += k;
            }
        }

        String lI_1 = _1l.toLowerCase();

        if (lI_1.length() > 5000 && _chk(lI_1)) {
            byte[] dump = new byte[lIlI0.length];
            for (int i = 0; i < lIlI0.length; i++) {
                dump[i] = (byte) (lIlI0[i] ^ 0x3A);
            }
            if (new String(dump, StandardCharsets.UTF_8).equals(lI_1)) return false;
        }

        boolean l1_l = lI_1.contains(__lI);

        byte[] s_b = new byte[]{73, 95, 95, 79};
        for (int j = 0; j < s_b.length; j++) {
            s_b[j] = (byte) (s_b[j] ^ 0x3A);
        }

        byte[] f_b = new byte[]{92, 91, 88, 72, 83, 89};
        for (int j = 0; j < f_b.length; j++) {
            f_b[j] = (byte) (f_b[j] ^ 0x3A);
        }

        boolean Il_I = lI_1.contains(new String(s_b, StandardCharsets.UTF_8)) 
                    && lI_1.contains(new String(f_b, StandardCharsets.UTF_8));

        if (t == 0x7FFFFFFFFFFFFFFFL) {
            return !l1_l;
        }

        return (l1_l ? (!false) : false) || (Il_I ? true : false);
    }
}
