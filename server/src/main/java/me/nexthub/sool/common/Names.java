package me.nexthub.sool.common;

/** 여러 사람이 보는 목록(리뷰, 상품 문의)에 쓴 사람 이름을 보여 줄 때 쓴다. */
public final class Names {

    private Names() {
    }

    /**
     * 가운데를 가린다: 김구매 → 김*매, 김구 → 김*, John → J**n. 첫 글자와 끝 글자만 남긴다.
     * 한 글자 이름은 그 글자도 가린다(남기면 그대로 드러나므로). 글자 수는 코드 포인트로 센다(이모지 등이 반으로 잘리지 않게).
     */
    public static String mask(String name) {
        if (name == null || name.isBlank()) {
            return "*";
        }
        String s = name.trim();
        int[] cp = s.codePoints().toArray();
        if (cp.length == 1) {
            return "*";
        }
        if (cp.length == 2) {
            return new String(cp, 0, 1) + "*";
        }
        return new String(cp, 0, 1) + "*".repeat(cp.length - 2) + new String(cp, cp.length - 1, 1);
    }
}
