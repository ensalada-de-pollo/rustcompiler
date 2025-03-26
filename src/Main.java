import generated.tinyRustLexer;
import generated.tinyRustParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) throws Exception {
        CharStream input = CharStreams.fromFileName("./src/input.tr");
        tinyRustLexer lexer = new tinyRustLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        try {
            tinyRustParser parser = new tinyRustParser(tokens);
            ParseTree tree = parser.program();

            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(new tinyRustListener(), tree);
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
        }
    }
}