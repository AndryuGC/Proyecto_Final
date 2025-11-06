package compressor;

public class Token {
    private final boolean literal;
    private final char ch;
    private final int distance;
    private final int length;

    private Token(boolean literal, char ch, int distance, int length) {
        this.literal = literal;
        this.ch = ch;
        this.distance = distance;
        this.length = length;
    }

    public static Token literal(char c) {
        return new Token(true, c, 0, 0);
    }

    public static Token ref(int distance, int length) {
        return new Token(false, '\0', distance, length);
    }

    public boolean isLiteral() { return literal; }
    public char getCh() { return ch; }
    public int getDistance() { return distance; }
    public int getLength() { return length; }
}
