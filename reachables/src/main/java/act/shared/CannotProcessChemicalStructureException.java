package act.shared;

public class CannotProcessChemicalStructureException extends Exception {
  private static final long serialVersionUID = 1L;

  public CannotProcessChemicalStructureException(String msg) {
    super(msg);
  }
}
