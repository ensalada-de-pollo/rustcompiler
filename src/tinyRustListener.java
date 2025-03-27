import generated.tinyRustBaseListener;
import generated.tinyRustParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeProperty;

import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class tinyRustListener extends tinyRustBaseListener implements ParseTreeListener {
  ParseTreeProperty<String> rustTree = new ParseTreeProperty<>();

  private final String IF = "IF";
  private final String ELSE = "ELSE";
  private final String ENDIF = "ENDIF";
  private final String ENDLOOP = "ENDLOOP";
  private final String LOOP = "LOOP";

  private static StringBuilder program; // 전체 프로그램의 흐름을 담는 변수
  private static FileWriter fw; // 결과를 파일에 작성하는 함수
  static char return_type = 'v'; // 함수의 반환 타입, void ? 'v' : 'i'
  static int localVar_curIdx; // 지역 변수의 인덱스
  static int ifLabelCnt;
  static int loopLabelCnt;
  static Map<String, Integer> localVarTable; // 지역 변수와 인덱스를 매핑하여 저장하는 hashMap
  static Map<String, String> funcTable; // 함수명과 함수 정보를 매핑하여 저장하는 hashMap
  private Map<ParserRuleContext, Integer> labelTable; // 현재 context와 label count를 매핑하여 저장하는 hashMap
  private Map<String, String> jmpOpTable; // 각 조건 분기문에 대응하는 명령어를 모아놓은 hashMap, 반대 명령어와 서로 매핑


  // 지역 변수 선언을 처리하는 함수
  private static void assignLocalVar(String VarName) {
    if (!localVarTable.containsKey(VarName))
      localVarTable.put(VarName, localVar_curIdx++);
  }

  // 지역 변수의 이름과 매핑된 인덱스를 반환하는 함수
  private static int getLocalVarIndex(String VarName) {
    return localVarTable.get(VarName);
  }

  // 호출된 함수 이름을 함수 정보로 변경
  private static String replaceFuncCall() {
    String result = program.toString();

    for (String funcName : funcTable.keySet()) {
      Pattern pattern = Pattern.compile("\\{" + funcName + "}");
      Matcher matcher = pattern.matcher(result);
      result = matcher.replaceAll(funcTable.get(funcName));
    }

    return result;
  }

  // 프로그램을 시작하는 함수
  // hashMap 등 자료구조 생성자를 포함
  @Override
  public void enterProgram(tinyRustParser.ProgramContext ctx) {
    File output = new File("./src/result.j");

    // 전역 변수 초기화
    program = new StringBuilder();
    localVarTable = new HashMap<>();
    funcTable = new HashMap<>();
    labelTable = new HashMap<>();
    jmpOpTable = new HashMap<>();

    // jmpOpTable에 각각에 대응되는 조건 분기 명령어 쌍을 추가
    jmpOpTable.put("if_icmpne", "if_icmpeq ");
    jmpOpTable.put("if_icmpeq", "if_icmpne ");
    jmpOpTable.put("if_icmple", "if_icmpgt ");
    jmpOpTable.put("if_icmplt", "if_icmpge ");
    jmpOpTable.put("if_icmpge", "if_icmplt ");
    jmpOpTable.put("if_icmpgt", "if_icmple ");

    try {
      if (!output.exists()) {
        if (!output.createNewFile())
          throw new Exception("파일 생성 실패");
      }

      // main 함수의 정보는 고정적이므로 하드 코딩으로 작성
      fw = new FileWriter(output);
      fw.write("""
                    .class public Main
                    .super java/lang/Object
                    ; standard initializer
                    .method public <init>()V
                    aload_0
                    invokenonvirtual java/lang/Object/<init>()V
                    return
                    .end method
                    
                    """);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  // 프로그램을 종료하는 함수
  @Override
  public void exitProgram(tinyRustParser.ProgramContext ctx) {
    // decl에 저장되어 있는 node를 하나하나 읽어와서 StringBuilder에 append
    for (int i = 0; i < ctx.decl().size(); i++)
      program.append(rustTree.get(ctx.decl(i)));

    // 함수명으로 기재된 함수 부분을 함수 정보로 변경
    String result = replaceFuncCall();

    try {
      fw.write(result);
      fw.flush();
      fw.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void exitDecl(tinyRustParser.DeclContext ctx) {
    String decl = "";

    if (ctx.main_decl() != null)
      decl = rustTree.get(ctx.main_decl());
    else if (ctx.fun_decl() != null)
      decl = rustTree.get(ctx.fun_decl());

    // 로컬 변수의 scope를 감안하여 index의 값을 0으로 세팅
    localVar_curIdx = 0;
    return_type = 'v';

    rustTree.put(ctx, decl);
  }

  @Override
  public void exitMain_decl(tinyRustParser.Main_declContext ctx) {
    String result = "\n\n.method public static main([Ljava/lang/String;)V\n" +
                    ".limit stack 32\n" +
                    ".limit locals 32\n";

    result += rustTree.get(ctx.compound_stmt());
    result += "\n.end method\n\n";

    // main 함수의 정보를 함수명과 함께 funcTable에 put
    funcTable.put("main", "main([Ljava/lang/String;)V\n");

    rustTree.put(ctx, result);
  }

  @Override
  public void exitRet_type_spec(tinyRustParser.Ret_type_specContext ctx) {
    if (ctx.type_spec() != null)
      return_type = 'i';
  }

  @Override
  public void exitFun_decl(tinyRustParser.Fun_declContext ctx) {
    String result = "\n\n.method public static ";

    String funcInfo = rustTree.get(ctx.id()) + "(" + rustTree.get(ctx.params()) + ")";
    funcInfo += return_type == 'i' ? "I" : "V";
    funcTable.put(rustTree.get(ctx.id()), funcInfo);

    result += funcInfo + "\n";
    result += ".limit stack 32\n" +
              ".limit locals 32\n";
    result += rustTree.get(ctx.compound_stmt());
    result += "\n.end method\n\n";

    rustTree.put(ctx, result);
  }

  @Override
  public void exitParams(tinyRustParser .ParamsContext ctx) {
    // 파라미터의 타입은 int로 고정이므로, 파라미터 개수만 지정
    rustTree.put(ctx, "I".repeat(ctx.param().size()));
  }

  @Override
  public void exitParam(tinyRustParser.ParamContext ctx) {
    // 파라미터로 들어온 값 또한 지역 변수에 해당하므로 지역 변수 테이블에 추가
    assignLocalVar(rustTree.get(ctx.id()));
  }

  @Override
  public void exitCompound_stmt(tinyRustParser.Compound_stmtContext ctx) {
    StringBuilder result = new StringBuilder();

    for (int i = 0; i < ctx.local_decl().size(); i++)
      result.append(rustTree.get(ctx.local_decl(i)));

    for (int i = 0; i < ctx.stmt().size(); i++)
      result.append(rustTree.get(ctx.stmt(i)));

    rustTree.put(ctx, result.toString());
  }

  @Override
  public void exitLocal_decl(tinyRustParser.Local_declContext ctx) {
    String result = "istore_";
    String id = rustTree.get(ctx.id());
    String val = rustTree.get(ctx.val());

    if (localVarTable.containsKey(id)) {
      result += getLocalVarIndex(id);
    } else {
      result += localVar_curIdx;
      assignLocalVar(id);
    }

    rustTree.put(ctx, val + result + "\n");
  }

  @Override
  public void exitVal(tinyRustParser.ValContext ctx) {
    String result = "";

    if (ctx.literal() != null)
      result = "bipush " + rustTree.get(ctx.literal());
    else if (ctx.id() != null)
      result += "iload_" + getLocalVarIndex(rustTree.get(ctx.id()));

    rustTree.put(ctx, result + "\n");
  }

  @Override
  public void exitStmt(tinyRustParser.StmtContext ctx) {
    String result = "";
    if (ctx.expr_stmt() != null)
      result = rustTree.get(ctx.expr_stmt());
    else if (ctx.assignment_stmt() != null)
      result = rustTree.get(ctx.assignment_stmt());
    else if (ctx.compound_stmt() != null)
      result = rustTree.get(ctx.compound_stmt());
    else if (ctx.return_stmt() != null)
      result = rustTree.get(ctx.return_stmt());
    else if (ctx.print_stmt() != null)
      result = rustTree.get(ctx.print_stmt());
    else if (ctx.if_stmt() != null)
      result = rustTree.get(ctx.if_stmt());
    else if (ctx.loop_stmt() != null)
      result = rustTree.get(ctx.loop_stmt());
    else if (ctx.break_stmt() != null)
      result = rustTree.get(ctx.break_stmt());
    else if (ctx.for_stmt() != null)
      result = rustTree.get(ctx.for_stmt());

    rustTree.put(ctx, result);
  }

  @Override
  public void exitExpr_stmt(tinyRustParser.Expr_stmtContext ctx) {
    rustTree.put(ctx, rustTree.get(ctx.expr()));
  }

  @Override
  public void exitExpr(tinyRustParser.ExprContext ctx) {
    rustTree.put(ctx, rustTree.get(ctx.relative_expr()));
  }

  @Override
  public void exitAdditive_expr(tinyRustParser.Additive_exprContext ctx) {
    String result = "";
    String op, left, right;

    if (ctx.additive_expr() != null) {
      left = rustTree.get(ctx.additive_expr());
      op = ctx.getChild(1).getText();

      if (op.equals("+"))
        op = "iadd\n";
      else if (op.equals("-"))
        op = "isub\n";

      right = rustTree.get(ctx.multiplicative_expr());
      result = left + right + op;
    } else {
      result = rustTree.get(ctx.multiplicative_expr());
    }

    rustTree.put(ctx, result);
  }

  @Override
  public void exitMultiplicative_expr(tinyRustParser.Multiplicative_exprContext ctx) {
    String result = "";
    String op, left, right;
    if (ctx.multiplicative_expr() != null) {
      left = rustTree.get(ctx.multiplicative_expr());
      op = ctx.getChild(1).getText();

      if (op.equals("*"))
        op = "imul\n";
      else if (op.equals("/"))
        op = "idiv\n";
      else if (op.equals("%"))
        op = "irem\n";

      right = rustTree.get(ctx.unary_expr());
      result = left + right + op;
    } else {
      result = rustTree.get(ctx.unary_expr());
    }

    rustTree.put(ctx, result);
  }

  @Override
  public void exitUnary_expr(tinyRustParser.Unary_exprContext ctx) {
    String result = rustTree.get(ctx.factor());
    rustTree.put(ctx, result);
  }

  @Override
  public void exitFactor(tinyRustParser.FactorContext ctx) {
    String result = "";

    if (ctx.args() != null) {
      if (funcTable.containsKey(rustTree.get(ctx.id()))) {
        result = rustTree.get(ctx.args()) +
            "invokestatic Main/" +
            funcTable.get(rustTree.get(ctx.id())) +
            "\n";
      } else {
        result = rustTree.get(ctx.args()) +
            "invokestatic Main/{" +
            rustTree.get(ctx.id()) +
            "}\n";
      }
    } else if (ctx.id() != null) {
      result = "iload_" + getLocalVarIndex(rustTree.get(ctx.id())) + "\n";
    } else if (ctx.literal() != null) {
      result = "bipush " + rustTree.get(ctx.literal()) + "\n";
    } else if (ctx.expr() != null) {
      result = rustTree.get(ctx.expr());
    }

    rustTree.put(ctx, result);
  }

  @Override
  public void exitArgs(tinyRustParser.ArgsContext ctx) {
    String result = "";

    if (ctx.expr() != null) {
      for (int i = 0; i < ctx.expr().size(); i++)
        result += rustTree.get(ctx.expr(i));
    }

    rustTree.put(ctx, result);
  }

  @Override
  public void exitComparative_expr(tinyRustParser.Comparative_exprContext ctx) {
    String result = "";
    String op, left, right;

    if (ctx.comparative_expr() != null) {
      left = rustTree.get(ctx.comparative_expr());
      op = ctx.getChild(1).getText();
      right = rustTree.get(ctx.comparative_expr());

      if (op.equals("=="))
        op = "if_icmpne ";
      else if (op.equals("!="))
        op = "if_icmpeq ";
      else if (op.equals("<="))
        op = "if_icmpgt ";
      else if (op.equals("<"))
        op = "if_icmpge ";
      else if (op.equals(">="))
        op = "if_icmplt ";
      else if (op.equals(">"))
        op = "if_icmple ";

      result = left + right + op + ELSE + (ifLabelCnt - 1) + "\n";
    } else {
      result = rustTree.get(ctx.additive_expr());
    }

    rustTree.put(ctx, result);
  }

  @Override
  public void exitRelative_expr(tinyRustParser.Relative_exprContext ctx) {
    String result = "";
    String op, left, right;

    if (ctx.relative_expr() != null) {
      left = rustTree.get(ctx.relative_expr());
      op = ctx.getChild(1).getText();
      right = rustTree.get(ctx.comparative_expr());

      String[] tmp = left.split("\n");
      String cmpOp = tmp[tmp.length - 1].split(" ")[0];

      if (op.equals("||")) {
        if (jmpOpTable.containsKey(cmpOp))
          cmpOp = jmpOpTable.get(cmpOp) + IF + (ifLabelCnt - 1) + "\n";
        else
          cmpOp = "";

        left = "";

        for (int i = 0; i < tmp.length; i++)
          left += tmp[i] + "\n";

        left += cmpOp;
      }

      result += left;
      result += right;
    } else {
      result = rustTree.get(ctx.comparative_expr());
    }

    rustTree.put(ctx, result);
  }

  @Override
  public void enterIf_stmt(tinyRustParser.If_stmtContext ctx) {
    labelTable.put(ctx, ifLabelCnt++);
  }

  @Override
  public void exitIf_stmt(tinyRustParser.If_stmtContext ctx) {
    StringBuilder result = new StringBuilder();

    int label = labelTable.get(ctx);

    result.append(rustTree.get(ctx.relative_expr())).append("\n")
        .append(IF).append(label).append(":\n")
        .append(rustTree.get(ctx.compound_stmt(0)))
        .append("goto ").append(ENDIF).append(label).append("\n\n")
        .append(ELSE).append(label).append(":\n");

    if (ctx.ELSE() != null)
      result.append(rustTree.get(ctx.compound_stmt(1)));

    result.append("\n").append(ENDIF).append(label).append(":\n");
    rustTree.put(ctx, result.toString());
  }

  @Override
  public void enterBreak_stmt(tinyRustParser.Break_stmtContext ctx) {
    labelTable.put(ctx, loopLabelCnt - 1);
  }

  @Override
  public void exitBreak_stmt(tinyRustParser.Break_stmtContext ctx) {
    rustTree.put(ctx, "goto " + ENDLOOP + labelTable.get(ctx) + "\n");
  }

  @Override
  public void enterFor_stmt(tinyRustParser.For_stmtContext ctx) {
    labelTable.put(ctx, loopLabelCnt++);

    assignLocalVar(ctx.id().getText());
  }

  @Override
  public void exitFor_stmt(tinyRustParser.For_stmtContext ctx) {
    StringBuilder result = new StringBuilder();

    int label = labelTable.get(ctx);

    String[] range = rustTree.get(ctx.range()).split(" ");
    assignLocalVar("$");

    result.append("bipush ").append(range[0]).append("\n")
        .append("istore_").append(getLocalVarIndex(rustTree.get(ctx.id()))).append("\n\n")
        .append(LOOP).append(label).append(":\n")
        .append("iload_").append(getLocalVarIndex(rustTree.get(ctx.id()))).append("\n")
        .append("bipush ").append(range[1]).append("\n")
        .append("if_icmpge ").append(ENDLOOP).append(label).append(":\n")
        .append(rustTree.get(ctx.compound_stmt()))
        .append("iload_").append(getLocalVarIndex(rustTree.get(ctx.id()))).append("\n")
        .append("bipush 1\n")
        .append("iadd\n")
        .append("istore_").append(getLocalVarIndex(rustTree.get(ctx.id()))).append("\n")
        .append("goto ").append(LOOP).append(label).append("\n\n")
        .append(ENDLOOP).append(label).append(":\n");

    rustTree.put(ctx, result.toString());
  }

  @Override
  public void exitRange(tinyRustParser.RangeContext ctx) {
    String result = rustTree.get(ctx.literal(0));
    result += " ";

    if (ctx.getChildCount() == 4)
      result += Integer.parseInt(rustTree.get(ctx.literal(1))) + 1;
    else
      result += rustTree.get(ctx.literal(1));

    rustTree.put(ctx, result);
  }

  @Override
  public void enterLoop_stmt(tinyRustParser.Loop_stmtContext ctx) {
    labelTable.put(ctx, loopLabelCnt++);
  }

  @Override
  public void exitLoop_stmt(tinyRustParser.Loop_stmtContext ctx) {
    StringBuilder result = new StringBuilder();

    int label = labelTable.get(ctx);

    result.append("\n").append(LOOP).append(label).append(":\n")
        .append(rustTree.get(ctx.compound_stmt()))
        .append("goto ").append(LOOP).append(label).append("\n\n")
        .append(ENDLOOP).append(label).append(":\n");

    rustTree.put(ctx, result.toString());
  }

  @Override
  public void exitAssignment_stmt(tinyRustParser.Assignment_stmtContext ctx) {
    String expr = rustTree.get(ctx.expr());
    String result = expr + "istore_" + getLocalVarIndex(rustTree.get(ctx.id())) + "\n";
    rustTree.put(ctx, result);
  }

  @Override
  public void exitPrint_stmt(tinyRustParser.Print_stmtContext ctx) {
    String result = "getstatic java/lang/System/out Ljava/io/PrintStream;\n";
    result += "iload_" + getLocalVarIndex(rustTree.get(ctx.id())) + "\n";
    result += "invokevirtual java/io/PrintStream.println(I)V\n";
    rustTree.put(ctx, result);
  }

  @Override
  public void exitReturn_stmt(tinyRustParser.Return_stmtContext ctx) {
    if (return_type == 'i')
      rustTree.put(ctx, rustTree.get(ctx.expr()) + "ireturn\n");
    else
      rustTree.put(ctx, "return\n");
  }

  @Override
  public void exitLiteral(tinyRustParser.LiteralContext ctx) {
    rustTree.put(ctx, ctx.LITERAL().getText());
  }

  @Override
  public void exitId(tinyRustParser.IdContext ctx) {
    rustTree.put(ctx, ctx.ID().getText());
  }
}