interface Chim { default String keu() { return "Chíp chíp"; } }
interface DoChoi { default String keu() { return "Bíp bíp"; } }

public class Diamond implements Chim, DoChoi { }
