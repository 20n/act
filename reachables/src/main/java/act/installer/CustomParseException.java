package act.installer;

public class CustomParseException extends Exception { 
	CustomParseException(String msg) { super(msg); }
	CustomParseException() { super(); }
}
class CiderInvalidBeeNameRefException extends CustomParseException {}
class CiderInvalidMsgRefException extends CustomParseException {}
class CiderInvalidRepostFormatException extends CustomParseException {}
class CiderSwapSpaceException extends CustomParseException {
    CiderSwapSpaceException(String msg) { super(msg); }
    CiderSwapSpaceException() { super(); }
}
class CiderNoBeeReadyException extends CustomParseException {}
class CiderPubmedFormatException extends CustomParseException {
    CiderPubmedFormatException(String msg) { super(msg); }
    CiderPubmedFormatException() { super(); }
}
