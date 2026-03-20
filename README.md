RoboGame - Robot Language Parser & Interpreter
Implementing a parser and interpreter for a custom domain-specific language used to control robots in a 2D grid-based survival game.
What It Is
Two robots compete on a 12x12 grid, consuming fuel each step. The last robot with fuel wins. Each robot is controlled by a program written in a custom scripting language — this project is the parser and interpreter that reads, validates, and executes those programs.
Parser & Language Design
Built as a recursive descent parser — each grammar rule maps directly to a parse method that reads tokens from a Scanner and builds an AST node. The language supports:

Actions, loops, conditionals, and while loops
Sensor reads (distance to wall, opponent position, barrel locations)
Arithmetic and logical expressions in prefix/functional form e.g. add(fuelLeft, 3)
Variables with program-wide scope, independent per robot

The parser was built incrementally across 4 stages, each adding complexity on top of the last without breaking what came before.
OOP Design
Three interfaces drive the whole tree:
InterfaceRoleExample nodesProgramNodeStatements — have execute(Robot)loopNode, ifNode, moveNodeBoolNodeConditions — have evaluate(Robot) -> booleanltNode, andNode, notNodeIntNodeExpressions — have evaluate(Robot) -> intaddNode, fuelLeftNode, varNode
Each node class is self-contained with a constructor, toString() for printing the AST, and an execute()/evaluate() method for running it. The tree is walked recursively at runtime.
Stages of Complexity
StageWhat was added0Basic actions (move, turnL, turnR, takeFuel, wait), infinite loop1if, while, robot sensor reads, relational conditions (lt, gt, eq)2Arithmetic operators, else clause, optional arguments for move/wait, logical operators (and, or, not)3elif chains, indexed barrel sensors, variables ($x = expr), per-robot independent variable storage
Tech
Java — no external libraries. Compile and run with javac *.java.
