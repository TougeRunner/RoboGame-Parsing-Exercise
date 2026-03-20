import java.util.*;
import java.util.regex.*;

/**
 * See assignment handout for the grammar.
 * You need to implement the parse(..) method and all the rest of the parser.
 * There are several methods provided for you:
 * - several utility methods to help with the parsing
 * See also the TestParser class for testing your code.
 */
public class Parser {


    // Useful Patterns

    static final Pattern NUMPAT = Pattern.compile("-?[1-9][0-9]*|0"); 
    static final Pattern OPENPAREN = Pattern.compile("\\(");
    static final Pattern CLOSEPAREN = Pattern.compile("\\)");
    static final Pattern OPENBRACE = Pattern.compile("\\{");
    static final Pattern CLOSEBRACE = Pattern.compile("\\}");

    // --- My added patterns vvv ---

    static final Pattern SEMICOLON = Pattern.compile(";");
    static final Pattern COMMA     = Pattern.compile(",");

    // All action keywords across stages 1-3 (ran out of time for 4 ), used to recognise an ACT token in parse_STMT
    static final Pattern ACT = Pattern.compile("move|turnL|turnR|turnAround|shieldOn|shieldOff|takeFuel|wait");

    static final Pattern LOOP  = Pattern.compile("loop");
    static final Pattern IF    = Pattern.compile("if");
    static final Pattern ELIF  = Pattern.compile("elif");
    static final Pattern ELSE  = Pattern.compile("else");
    static final Pattern WHILE = Pattern.compile("while");

    // Relational operators used in conditions (substitute for <|>|== which are reserved in Java and can't be used as tokens)
    static final Pattern RELOP = Pattern.compile("lt|gt|eq");

    // Logical operators used in compound conditions
    static final Pattern LOGOP = Pattern.compile("and|or|not");

    // math operators used in expressions
    static final Pattern OP = Pattern.compile("add|sub|mul|div");

    // Sensor keywords - these read from the robot and return integers, so they are IntNodes and can be used inside expressions as well as conditions (vs code is really predicting my notes bruh)
    static final Pattern SENS = Pattern.compile(
        "fuelLeft|oppLR|oppFB|numBarrels|barrelLR|barrelFB|wallDist");

    // Variable names: must start with $ then a letter, then any letters/digits
    static final Pattern VAR = Pattern.compile("\\$[A-Za-z][A-Za-z0-9]*");

    //----------------------------------------------------------------
    /**
     * The top of the parser, which is handed a scanner containing
     * the text of the program to parse.
     * Returns the parse tree.
     */
    ProgramNode parse(Scanner s) {
        // Set the delimiter for the scanner.
        s.useDelimiter("\\s+|(?=[{}(),;])|(?<=[{}(),;])");
        // THE PARSER GOES HERE
        // Call the parseProg method for the first grammar rule (PROG) and return the node
        return parse_PROG(s); // kick off the whole parse from the top-level rule
    }

    // ===========================================================
    // PROG ::= [ STMT ]*
    // ===========================================================

    /** parse_PROG: entry point - collects zero or more statements into a blockNode
     *  think of this as just "grab everything until we run out of tokens" **/
    public ProgramNode parse_PROG(Scanner s) {

        List<ProgramNode> stmts = new ArrayList<ProgramNode>(); // list to hold every statement we find

        while (s.hasNext()) { // keep going until no tokens left
            stmts.add(parse_STMT(s)); // parse one statement at a time and add it to the list
        }

        return new blockNode(stmts); // wrap the whole list in a block and hand it back
    }

    // ===========================================================
    // STMT ::= ACT ";" | LOOP | IF | WHILE | ASSGN ";"
    // ===========================================================

    /** parse_STMT: figures out what kind of statement is next and routes to the right parser
     *  basically a big traffic cop - peek at the next token and decide where to send it **/
    public ProgramNode parse_STMT(Scanner s) {

        if (s.hasNext(ACT)) { // it's one of the action keywords (move, turnL, etc)
            ProgramNode p = parse_ACT(s);
            require(SEMICOLON, "Expecting ';' after action", s); // actions always end with ;
            return p;

        } else if (s.hasNext(LOOP)) { 
            return parse_LOOP(s);

        } else if (s.hasNext(IF)) { 
            return parse_IF(s);

        } else if (s.hasNext(WHILE)) { 
            return parse_WHILE(s);

        } else if (s.hasNext(VAR)) { // it's a variable assignment like $x = 3
            ProgramNode p = parse_ASSGN(s);
            require(SEMICOLON, "Expecting ';' after assignment", s); // assignments also end with ;
            return p;
        }

        fail("Unrecognised statement", s); // nothing matched - the program has something invalid
        return null;
    }

    // ===========================================================
    // ACT ::= "move" ["(" EXPR ")"] | "turnL" | "turnR" | "turnAround"
    //       | "shieldOn" | "shieldOff" | "takeFuel" | "wait" ["(" EXPR ")"]
    // ===========================================================

    /** parse_ACT: consumes the action keyword and builds the right node
     *  move and wait are special because they can take an optional expression argument!!!! **/
    public ProgramNode parse_ACT(Scanner s) {

        String token = s.next(); // consume the action keyword so I can use switch on it

        switch (token) {

            case "move": {
                // move can optionally have an expression in parens: move(3) or move(fuelLeft)
                if (s.hasNext(OPENPAREN)) {
                    s.next(); // consume the '('
                    IntNode expr = parse_EXPR(s); // parse what's inside the parens
                    require(CLOSEPAREN, "Expecting ')' after move argument", s);
                    return new moveNode(expr); // move node with a repeat count
                }
                return new moveNode(null); // plain move, no argument = move once
            }

            case "wait": {
                // wait works the same as move - optional expression argument
                if (s.hasNext(OPENPAREN)) {
                    s.next(); // consume the '('
                    IntNode expr = parse_EXPR(s);
                    require(CLOSEPAREN, "Expecting ')' after wait argument", s);
                    return new wait_Node(expr);
                }
                return new wait_Node(null); // plain wait, no argument = wait once
            }

            case "turnL":      return new turnL_Node();
            case "turnR":      return new turnR_Node();
            case "turnAround": return new turnAround_Node();
            case "shieldOn":   return new shieldOn_Node();
            case "shieldOff":  return new shieldOff_Node();
            case "takeFuel":   return new takeFuel_Node();

            default:
                fail("Unknown action: " + token, s);
                return null;
        }
    }

    // ===========================================================
    // LOOP ::= "loop" BLOCK
    // ===========================================================

    /** parse_LOOP: consumes 'loop' keyword then grabs the block that follows **/
    public ProgramNode parse_LOOP(Scanner s) {

        require(LOOP, "Expecting 'loop'", s); // consume the 'loop' keyword

        blockNode b = (blockNode) parse_block(s); // grab the { ... } body

        return new loopNode(b); // wrap block in a loopNode that runs it forever
    }

    // ===========================================================
    // IF ::= "if" "(" COND ")" BLOCK ["elif" "(" COND ")" BLOCK]* ["else" BLOCK]
    // ===========================================================

    /** parse_IF: handles if, any number of elif branches, and an optional else
     *  the elif chain gets built into nested ifNodes from back to front so the
     *  structure mirrors how they actually execute (check first true branch) **/
    public ProgramNode parse_IF(Scanner s) {

        require(IF, "Expecting 'if'", s);
        require(OPENPAREN, "Expecting '(' after 'if'", s);
        BoolNode cond = parse_COND(s); // the condition to test
        require(CLOSEPAREN, "Expecting ')' after condition", s);
        blockNode thenBlock = (blockNode) parse_block(s); // the body to run if true

        // collect all elif branches into lists so we can chain them later
        List<BoolNode>  elifConds  = new ArrayList<BoolNode>();
        List<blockNode> elifBlocks = new ArrayList<blockNode>();

        while (s.hasNext(ELIF)) { // keep consuming elif branches as long as they exist
            s.next(); // consume 'elif'
            require(OPENPAREN, "Expecting '(' after 'elif'", s);
            elifConds.add(parse_COND(s));
            require(CLOSEPAREN, "Expecting ')' after elif condition", s);
            elifBlocks.add((blockNode) parse_block(s));
        }

        // optional else block at the very end
        blockNode elseBlock = null;
        if (s.hasNext(ELSE)) {
            s.next(); // consume 'else'
            elseBlock = (blockNode) parse_block(s);
        }

        // build the elif chain from the back - last elif wraps the else,
        // then each prior elif wraps the next, giving us a nested ifNode tree
        ProgramNode elseBranch = elseBlock; // start from the bottom of the chain
        for (int i = elifConds.size() - 1; i >= 0; i--) {
            elseBranch = new ifNode(elifConds.get(i), elifBlocks.get(i), elseBranch);
        }

        return new ifNode(cond, thenBlock, elseBranch); // outermost if wraps everything it is like this so that the order remains the same for example : if(elif(elif(else()))))) thats how the code will execute and we want the structure of the parse tree to mirror that
    }

    // ===========================================================
    // WHILE ::= "while" "(" COND ")" BLOCK
    // ===========================================================

    /** parse_WHILE: standard while loop - condition then body block **/
    public ProgramNode parse_WHILE(Scanner s) {

        require(WHILE, "Expecting 'while'", s);
        require(OPENPAREN, "Expecting '(' after 'while'", s);
        BoolNode cond = parse_COND(s);
        require(CLOSEPAREN, "Expecting ')' after condition", s);
        blockNode block = (blockNode) parse_block(s);

        return new whileNode(cond, block);
    }

    // ===========================================================
    // BLOCK ::= "{" STMT+ "}"
    // ===========================================================

    /** parse_block: grabs one or more statements wrapped in curly braces **/
    public ProgramNode parse_block(Scanner s) {

        List<ProgramNode> stmts = new ArrayList<ProgramNode>();

        require(OPENBRACE, "Expecting '{'", s);

        do { // must have at least one statement - grammar says STMT+
            stmts.add(parse_STMT(s));
        } while (!s.hasNext(CLOSEBRACE)); // keep going until we see the closing brace

        require(CLOSEBRACE, "Expecting '}'", s);

        return new blockNode(stmts);
    }

    // ===========================================================
    // COND ::= RELOP "(" EXPR "," EXPR ")"
    //        | "and" "(" COND "," COND ")"
    //        | "or"  "(" COND "," COND ")"
    //        | "not" "(" COND ")"
    // ===========================================================

    /** parse_COND: parses something that evaluates to true or false
     *  either a relational comparison (lt/gt/eq) or a logical combo (and/or/not) **/
    public BoolNode parse_COND(Scanner s) {

        if (s.hasNext(RELOP)) { // lt(...), gt(...), eq(...)
            String op = s.next(); // consume the operator
            require(OPENPAREN, "Expecting '(' after relational operator", s);
            IntNode left  = parse_EXPR(s);
            require(COMMA, "Expecting ',' between expressions", s);
            IntNode right = parse_EXPR(s);
            require(CLOSEPAREN, "Expecting ')' after condition arguments", s);

            switch (op) {
                case "lt": return new ltNode(left, right); // less than
                case "gt": return new gtNode(left, right); // greater than
                case "eq": return new eqNode(left, right); // equal to
                default:
                    fail("Unknown relational operator: " + op, s);
                    return null;
            }

        } else if (s.hasNext(LOGOP)) { // and(...), or(...), not(...)
            String op = s.next(); // consume the logical operator
            require(OPENPAREN, "Expecting '(' after logical operator", s);

            switch (op) {
                case "and": {
                    BoolNode left  = parse_COND(s); // first condition
                    require(COMMA, "Expecting ',' in and()", s);
                    BoolNode right = parse_COND(s); // second condition
                    require(CLOSEPAREN, "Expecting ')' after and() arguments", s);
                    return new andNode(left, right);
                }
                case "or": {
                    BoolNode left  = parse_COND(s);
                    require(COMMA, "Expecting ',' in or()", s);
                    BoolNode right = parse_COND(s);
                    require(CLOSEPAREN, "Expecting ')' after or() arguments", s);
                    return new orNode(left, right);
                }
                case "not": {
                    BoolNode inner = parse_COND(s); // only one condition inside not
                    require(CLOSEPAREN, "Expecting ')' after not() argument", s);
                    return new notNode(inner);
                }
                default:
                    fail("Unknown logical operator: " + op, s);
                    return null;
            }
        }

        fail("Expecting a condition (lt/gt/eq/and/or/not)", s);
        return null;
    }

    // ===========================================================
    // EXPR ::= NUM | SENS | VAR | OP "(" EXPR "," EXPR ")"
    // ===========================================================

    /** parse_EXPR: parses something that evaluates to an integer
     *  can be a number, sensor read, variable, or arithmetic operation **/
    public IntNode parse_EXPR(Scanner s) {

        if (s.hasNext(NUMPAT)) { // plain integer literal like 3 or -5
            int val = Integer.parseInt(s.next());
            return new numNode(val);

        } else if (s.hasNext(OP)) { // mathematical: add(...), sub(...), mul(...), div(...)
            String op = s.next(); // consume the operator
            require(OPENPAREN, "Expecting '(' after arithmetic operator", s);
            IntNode left  = parse_EXPR(s); // left operand (recursive - can be any expr)
            require(COMMA, "Expecting ',' between expressions", s);
            IntNode right = parse_EXPR(s); // right operand
            require(CLOSEPAREN, "Expecting ')' after arithmetic arguments", s);

            switch (op) {
                case "add": return new addNode(left, right);
                case "sub": return new subNode(left, right);
                case "mul": return new mulNode(left, right);
                case "div": return new divNode(left, right);
                default:
                    fail("Unknown arithmetic operator: " + op, s);
                    return null;
            }

        } else if (s.hasNext(SENS)) { // sensor read like fuelLeft or barrelLR
            return parse_SENS(s);

        } else if (s.hasNext(VAR)) { // variable like $myVar
            String name = s.next();
            return new varNode(name);
        }

        fail("Expecting an expression (number, sensor, variable, or operator)", s);
        return null;
    }

    // ===========================================================
    // SENS ::= "fuelLeft" | "oppLR" | "oppFB" | "numBarrels"
    //        | "barrelLR" ["(" EXPR ")"] | "barrelFB" ["(" EXPR ")"]
    //        | "wallDist"
    // ===========================================================

    /** parse_SENS: parses a sensor keyword and returns the right sensor node
     *  barrelLR and barrelFB are special because they can take an optional index argument **/
    public IntNode parse_SENS(Scanner s) {

        String sensor = s.next(); // consume the sensor keyword

        switch (sensor) {
            case "fuelLeft":   return new fuelLeftNode();
            case "oppLR":      return new oppLRNode();
            case "oppFB":      return new oppFBNode();
            case "numBarrels": return new numBarrelsNode();
            case "wallDist":   return new wallDistNode();

            case "barrelLR": {
                // barrelLR can be plain or barrelLR(expr) for nth barrel
                if (s.hasNext(OPENPAREN)) {
                    s.next(); // consume '('
                    IntNode index = parse_EXPR(s); // the index expression
                    require(CLOSEPAREN, "Expecting ')' after barrelLR index", s);
                    return new barrelLR_Node(index); // nth barrel version
                }
                return new barrelLR_Node(null); // no arg = closest barrel
            }

            case "barrelFB": {
                if (s.hasNext(OPENPAREN)) {
                    s.next();
                    IntNode index = parse_EXPR(s);
                    require(CLOSEPAREN, "Expecting ')' after barrelFB index", s);
                    return new barrelFB_Node(index);
                }
                return new barrelFB_Node(null);
            }

            default:
                fail("Unknown sensor: " + sensor, s);
                return null;
        }
    }

    // ===========================================================
    // ASSGN ::= VAR "=" EXPR
    // ===========================================================

    /** parse_ASSGN: parses a variable assignment like $x = add(3, fuelLeft)
     *  the semicolon is consumed by parse_STMT after this returns **/
    public ProgramNode parse_ASSGN(Scanner s) {

        String varName = s.next(); // consume the variable name e.g. $x
        require("=", "Expecting '=' in assignment", s);
        IntNode expr = parse_EXPR(s); // the value to assign
        return new assgnNode(varName, expr);
    }

    //----------------------------------------------------------------
    // utility methods for the parser
    // - fail(..) reports a failure and throws exception
    // - require(..) consumes and returns the next token as long as it matches the pattern
    // - requireInt(..) consumes and returns the next token as an int as long as it matches the pattern
    // - checkFor(..) peeks at the next token and only consumes it if it matches the pattern

    /**
     * Report a failure in the parser.
     */
    static void fail(String message, Scanner s) {
        String msg = message + "\n   @ ...";
        for (int i = 0; i < 5 && s.hasNext(); i++) {
            msg += " " + s.next();
        }
        throw new ParserFailureException(msg + "...");
    }

    /**
     * Requires that the next token matches a pattern if it matches, it consumes
     * and returns the token, if not, it throws an exception with an error
     * message
     */
    static String require(String p, String message, Scanner s) {
        if (s.hasNext(p)) {return s.next();}
        fail(message, s);
        return null;
    }

    static String require(Pattern p, String message, Scanner s) {
        if (s.hasNext(p)) {return s.next();}
        fail(message, s);
        return null;
    }

    /**
     * Requires that the next token matches a pattern (which should only match a
     * number) if it matches, it consumes and returns the token as an integer
     * if not, it throws an exception with an error message
     */
    static int requireInt(String p, String message, Scanner s) {
        if (s.hasNext(p) && s.hasNextInt()) {return s.nextInt();}
        fail(message, s);
        return -1;
    }

    static int requireInt(Pattern p, String message, Scanner s) {
        if (s.hasNext(p) && s.hasNextInt()) {return s.nextInt();}
        fail(message, s);
        return -1;
    }

    /**
     * Checks whether the next token in the scanner matches the specified
     * pattern, if so, consumes the token and return true. Otherwise returns
     * false without consuming anything.
     */
    static boolean checkFor(String p, Scanner s) {
        if (s.hasNext(p)) {s.next(); return true;}
        return false;
    }

    static boolean checkFor(Pattern p, Scanner s) {
        if (s.hasNext(p)) {s.next(); return true;} 
        return false;
    }

}

// You could add the node classes here or as separate java files.
// (if added here, they must not be declared public or private)
// For example:
//  class BlockNode implements ProgramNode {.....
//     with fields, a toString() method and an execute() method
//

// ===========================================================
// INTERFACES
// Beyond ProgramNode (already defined in ProgramNode.java),
// we need two more interfaces for things that evaluate to values:
//   BoolNode - evaluates to boolean (used by conditions)
//   IntNode  - evaluates to int (used by expressions and sensors)
// ===========================================================

/** BoolNode: anything that evaluates to true or false (conditions like lt, and, not) **/
interface BoolNode {
    boolean evaluate(Robot robot);
}

/** IntNode: anything that evaluates to an integer (sensors, numbers, arithmetic, variables) **/
interface IntNode {
    int evaluate(Robot robot);
}

// ===========================================================
// STATEMENT NODES - implement ProgramNode, have execute()
// ===========================================================

/** blockNode: holds a list of statements and runs them one by one in order
 *  used everywhere a { ... } block appears **/
class blockNode implements ProgramNode {

    final List<ProgramNode> stmts; // the list of statements inside this block

    /**constructor - just stores the list we parsed**/
    public blockNode(List<ProgramNode> stmts) {
        this.stmts = stmts;
    }

    /**toString: wraps all the statements in braces**/
    @Override
    public String toString() {
        String output = "{\n";
        for (ProgramNode s : stmts) {
            output = output + "  " + s.toString() + "\n";
        }
        return output + "}";
    }

    /**execute: runs each statement in the block in order**/
    public void execute(Robot robot) {
        for (ProgramNode stmt : stmts) { // foreach statement, run it
            stmt.execute(robot);
        }
    }
}

/** loopNode: runs its block forever until the robot runs out of fuel
 *  (RobotInterruptedException is what actually stops it) **/
class loopNode implements ProgramNode {

    final blockNode b; // the block to repeat

    public loopNode(blockNode b) {
        this.b = b;
    }

    @Override
    public String toString() {
        return "loop " + this.b.toString();
    }

    public void execute(Robot robot) {
        while (true) { // loops forever - robot exception will break it
            b.execute(robot);
        }
    }
}

/** ifNode: evaluates a condition, runs thenBlock if true, elseBranch if false
 *  elseBranch can be null (no else), a blockNode (else), or another ifNode (elif chain) **/
class ifNode implements ProgramNode {

    final BoolNode    cond;       // the condition to check
    final blockNode   thenBlock;  // run this if condition is true
    final ProgramNode elseBranch; // run this if false - can be null, blockNode, or ifNode

    /**constructor**/
    public ifNode(BoolNode cond, blockNode thenBlock, ProgramNode elseBranch) {
        this.cond       = cond;
        this.thenBlock  = thenBlock;
        this.elseBranch = elseBranch;
    }

    @Override
    public String toString() {
        String s = "if (" + cond + ") " + thenBlock.toString();
        if (elseBranch != null) {
            s = s + " else " + elseBranch.toString();
        }
        return s;
    }

    public void execute(Robot robot) {
        if (cond.evaluate(robot)) {
            thenBlock.execute(robot); // condition was true
        } else if (elseBranch != null) {
            elseBranch.execute(robot); // condition was false - try else/elif chain
        }
    }
}

/** whileNode: keeps running its block as long as the condition is true **/
class whileNode implements ProgramNode {

    final BoolNode  cond;  // condition to check each iteration
    final blockNode block; // body to run while true

    public whileNode(BoolNode cond, blockNode block) {
        this.cond  = cond;
        this.block = block;
    }

    @Override
    public String toString() {
        return "while (" + cond + ") " + block.toString();
    }

    public void execute(Robot robot) {
        while (cond.evaluate(robot)) { // re-evaluate condition each loop
            block.execute(robot);
        }
    }
}

/** assgnNode: evaluates an expression and stores the result in the robot's variable map
 *  variables are stored per-robot so two robots running the same program stay independent **/
class assgnNode implements ProgramNode {

    final String  varName; // the variable to assign to e.g. "$x"
    final IntNode expr;    // the expression whose value gets assigned

    public assgnNode(String varName, IntNode expr) {
        this.varName = varName;
        this.expr    = expr;
    }

    @Override
    public String toString() {
        return varName + " = " + expr;
    }

    public void execute(Robot robot) {
        int val = expr.evaluate(robot); // work out the value of the right-hand side
        // store it in THIS robot's variable map (each robot has its own map via robotVars)
        robotVars.getVars(robot).put(varName, val);
    }
}

// ===========================================================
// robotVars: per-robot variable storage
// Uses an IdentityHashMap so each Robot object gets its own variable map.
// This is how two robots running the same program stay independent -
// each robot looks up its own map, not a shared static one.
// ===========================================================
class robotVars {

    // IdentityHashMap keys on object identity (==) not equals()
    // so robot1 and robot2 get completely separate maps even if they are "equal"
    private static final Map<Robot, Map<String, Integer>> store =
        new IdentityHashMap<Robot, Map<String, Integer>>();

    /** getVars: returns the variable map for a given robot, creating it if it doesn't exist yet **/
    public static Map<String, Integer> getVars(Robot robot) {
        if (!store.containsKey(robot)) {
            store.put(robot, new HashMap<String, Integer>()); // first time this robot runs
        }
        return store.get(robot);
    }
}

// ===========================================================
// ACTION NODES - each calls one or more Robot methods
// ===========================================================

/** moveNode: moves the robot forward. Optional IntNode = how many times.
 *  null means no argument was given so just move once.
 *  Robot.move() has no repeat argument so we loop it ourselves. **/
class moveNode implements ProgramNode {

    final IntNode times; // null = no argument, otherwise evaluates to repeat count

    public moveNode(IntNode times) {
        this.times = times;
    }

    public String toString() {
        return (times == null) ? "move" : "move(" + times + ")";
    }

    public void execute(Robot robot) {
        int n = (times == null) ? 1 : times.evaluate(robot); // default to 1 if no arg
        for (int i = 0; i < n; i++) {
            robot.move();
        }
    }
}

/** wait_Node: makes robot idle. Optional IntNode = how many times to wait. **/
class wait_Node implements ProgramNode {

    final IntNode times;

    public wait_Node(IntNode times) {
        this.times = times;
    }

    public String toString() {
        return (times == null) ? "wait" : "wait(" + times + ")";
    }

    public void execute(Robot robot) {
        int n = (times == null) ? 1 : times.evaluate(robot);
        for (int i = 0; i < n; i++) {
            robot.idleWait();
        }
    }
}

/** these are all boring one-liners but they need to exist - one action each **/

class turnL_Node implements ProgramNode {
    public String toString() { return "turnL"; }
    public void execute(Robot robot) { robot.turnLeft(); }
}

class turnR_Node implements ProgramNode {
    public String toString() { return "turnR"; }
    public void execute(Robot robot) { robot.turnRight(); }
}

class turnAround_Node implements ProgramNode {
    public String toString() { return "turnAround"; }
    public void execute(Robot robot) { robot.turnAround(); }
}

class shieldOn_Node implements ProgramNode {
    public String toString() { return "shieldOn"; }
    public void execute(Robot robot) { robot.setShield(true); }
}

class shieldOff_Node implements ProgramNode {
    public String toString() { return "shieldOff"; }
    public void execute(Robot robot) { robot.setShield(false); }
}

class takeFuel_Node implements ProgramNode {
    public String toString() { return "takeFuel"; }
    public void execute(Robot robot) { robot.takeFuel(); }
}

// ===========================================================
// CONDITION NODES - implement BoolNode, have evaluate() -> boolean
// ===========================================================

/** ltNode: true if left < right **/
class ltNode implements BoolNode {
    final IntNode left, right;
    public ltNode(IntNode left, IntNode right) { this.left = left; this.right = right; }
    public String toString() { return "lt(" + left + ", " + right + ")"; }
    public boolean evaluate(Robot robot) { return left.evaluate(robot) < right.evaluate(robot); }
}

/** gtNode: true if left > right **/
class gtNode implements BoolNode {
    final IntNode left, right;
    public gtNode(IntNode left, IntNode right) { this.left = left; this.right = right; }
    public String toString() { return "gt(" + left + ", " + right + ")"; }
    public boolean evaluate(Robot robot) { return left.evaluate(robot) > right.evaluate(robot); }
}

/** eqNode: true if left == right **/
class eqNode implements BoolNode {
    final IntNode left, right;
    public eqNode(IntNode left, IntNode right) { this.left = left; this.right = right; }
    public String toString() { return "eq(" + left + ", " + right + ")"; }
    public boolean evaluate(Robot robot) { return left.evaluate(robot) == right.evaluate(robot); }
}

/** andNode: true only if BOTH conditions are true **/
class andNode implements BoolNode {
    final BoolNode left, right;
    public andNode(BoolNode left, BoolNode right) { this.left = left; this.right = right; }
    public String toString() { return "and(" + left + ", " + right + ")"; }
    public boolean evaluate(Robot robot) { return left.evaluate(robot) && right.evaluate(robot); }
}

/** orNode: true if AT LEAST ONE condition is true **/
class orNode implements BoolNode {
    final BoolNode left, right;
    public orNode(BoolNode left, BoolNode right) { this.left = left; this.right = right; }
    public String toString() { return "or(" + left + ", " + right + ")"; }
    public boolean evaluate(Robot robot) { return left.evaluate(robot) || right.evaluate(robot); }
}

/** notNode: flips the condition result (true becomes false, false becomes true) **/
class notNode implements BoolNode {
    final BoolNode inner;
    public notNode(BoolNode inner) { this.inner = inner; }
    public String toString() { return "not(" + inner + ")"; }
    public boolean evaluate(Robot robot) { return !inner.evaluate(robot); }
}

// ===========================================================
// EXPRESSION / SENSOR NODES - implement IntNode, have evaluate() -> int
// ===========================================================

/** numNode: a plain integer literal - just hands back the number it was built with **/
class numNode implements IntNode {
    final int val;
    public numNode(int val) { this.val = val; }
    public String toString() { return String.valueOf(val); }
    public int evaluate(Robot robot) { return val; }
}

/** varNode: reads the current value of a variable from this robot's variable map
 *  if the variable hasn't been set yet, returns 0 as per the spec **/
class varNode implements IntNode {
    final String name;
    public varNode(String name) { this.name = name; }
    public String toString() { return name; }
    public int evaluate(Robot robot) {
        Map<String, Integer> vars = robotVars.getVars(robot); // get THIS robot's map
        if (!vars.containsKey(name)) {
            vars.put(name, 0); // variable used before assignment defaults to 0
        }
        return vars.get(name);
    }
}

/** arithmetic nodes - all take two IntNode children and combine them **/

class addNode implements IntNode {
    final IntNode left, right;
    public addNode(IntNode left, IntNode right) { this.left = left; this.right = right; }
    public String toString() { return "add(" + left + ", " + right + ")"; }
    public int evaluate(Robot robot) { return left.evaluate(robot) + right.evaluate(robot); }
}

class subNode implements IntNode {
    final IntNode left, right;
    public subNode(IntNode left, IntNode right) { this.left = left; this.right = right; }
    public String toString() { return "sub(" + left + ", " + right + ")"; }
    public int evaluate(Robot robot) { return left.evaluate(robot) - right.evaluate(robot); }
}

class mulNode implements IntNode {
    final IntNode left, right;
    public mulNode(IntNode left, IntNode right) { this.left = left; this.right = right; }
    public String toString() { return "mul(" + left + ", " + right + ")"; }
    public int evaluate(Robot robot) { return left.evaluate(robot) * right.evaluate(robot); }
}

class divNode implements IntNode {
    final IntNode left, right;
    public divNode(IntNode left, IntNode right) { this.left = left; this.right = right; }
    public String toString() { return "div(" + left + ", " + right + ")"; }
    public int evaluate(Robot robot) { return left.evaluate(robot) / right.evaluate(robot); }
}

/** sensor nodes - each calls one robot method to read the environment **/

/** how much fuel the robot has remaining **/
class fuelLeftNode implements IntNode {
    public String toString() { return "fuelLeft"; }
    public int evaluate(Robot robot) { return robot.getFuel(); }
}

/** opponent's left/right offset relative to this robot (negative = left of us) **/
class oppLRNode implements IntNode {
    public String toString() { return "oppLR"; }
    public int evaluate(Robot robot) { return robot.getOpponentLR(); }
}

/** opponent's front/back offset relative to this robot (positive = in front) **/
class oppFBNode implements IntNode {
    public String toString() { return "oppFB"; }
    public int evaluate(Robot robot) { return robot.getOpponentFB(); }
}

/** how many fuel barrels are currently in the world **/
class numBarrelsNode implements IntNode {
    public String toString() { return "numBarrels"; }
    public int evaluate(Robot robot) { return robot.numBarrels(); }
}

/** distance to the wall directly in front of the robot **/
class wallDistNode implements IntNode {
    public String toString() { return "wallDist"; }
    public int evaluate(Robot robot) { return robot.getDistanceToWall(); }
}

/** barrelLR_Node: left/right offset of a barrel
 *  index == null means closest barrel, index != null means nth closest **/
class barrelLR_Node implements IntNode {

    final IntNode index; // null = no argument given (use closest barrel)

    public barrelLR_Node(IntNode index) { this.index = index; }

    public String toString() {
        return (index == null) ? "barrelLR" : "barrelLR(" + index + ")";
    }

    public int evaluate(Robot robot) {
        if (index == null) {
            return robot.getClosestBarrelLR(); // no arg = closest barrel
        }
        return robot.getBarrelLR(index.evaluate(robot)); // arg = nth barrel
    }
}

/** barrelFB_Node: front/back offset of a barrel
 *  index == null means closest barrel, index != null means nth closest **/
class barrelFB_Node implements IntNode {

    final IntNode index;

    public barrelFB_Node(IntNode index) { this.index = index; }

    public String toString() {
        return (index == null) ? "barrelFB" : "barrelFB(" + index + ")";
    }

    public int evaluate(Robot robot) {
        if (index == null) {
            return robot.getClosestBarrelFB();
        }
        return robot.getBarrelFB(index.evaluate(robot));
    }
}
// ===========================================================  