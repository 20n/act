package act.shared;

public class MalFormedReactionException extends Exception {
  private static final long serialVersionUID = 1L;

  public MalFormedReactionException(String msg) {
    super(msg);
  }
}
