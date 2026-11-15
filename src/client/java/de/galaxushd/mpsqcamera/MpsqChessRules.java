package de.galaxushd.mpsqcamera;

import java.util.*;

/** Deterministic chess position. Squares a1..h8 = 0..63; uppercase is white. */
public final class MpsqChessRules {
    public static final String START="rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    public record Move(int from,int to,char promotion) {
        public String uci(){return square(from)+square(to)+(promotion==0?"":String.valueOf(promotion));}
    }
    private final char[] board;
    private boolean white;
    private String rights;
    private int ep,halfmove,fullmove;
    public MpsqChessRules(){this(START);}
    public MpsqChessRules(String fen){
        String[] parts=fen.trim().split("\\s+");
        if(parts.length!=6)throw new IllegalArgumentException("Ungültige Schachstellung");
        board=new char[64];String[] ranks=parts[0].split("/");
        if(ranks.length!=8)throw new IllegalArgumentException("Acht Reihen erforderlich");
        for(int r=0;r<8;r++){int f=0;for(char c:ranks[r].toCharArray()){
            if(c>='1'&&c<='8')f+=c-'0';else {if(f>=8||"pnbrqkPNBRQK".indexOf(c)<0)throw new IllegalArgumentException("Ungültige Figur");board[(7-r)*8+f++]=c;}
        }if(f!=8)throw new IllegalArgumentException("Acht Felder erforderlich");}
        if(!parts[1].equals("w")&&!parts[1].equals("b"))throw new IllegalArgumentException("Ungültige Zugfarbe");
        white=parts[1].equals("w");rights=parts[2].equals("-")?"":parts[2];
        if(!rights.matches("K?Q?k?q?"))throw new IllegalArgumentException("Ungültige Rochaderechte");
        ep=parts[3].equals("-")?-1:index(parts[3]);halfmove=Integer.parseInt(parts[4]);fullmove=Integer.parseInt(parts[5]);
        if(halfmove<0||fullmove<1||count('K')!=1||count('k')!=1)throw new IllegalArgumentException("Ungültige Stellung");
        if(ep>=0&&(ep/8!=(white?5:2)||board[ep]!=0))throw new IllegalArgumentException("Ungültiges En-passant-Feld");
    }
    private MpsqChessRules(MpsqChessRules p){board=p.board.clone();white=p.white;rights=p.rights;ep=p.ep;halfmove=p.halfmove;fullmove=p.fullmove;}
    private int count(char c){int n=0;for(char p:board)if(p==c)n++;return n;}
    public char piece(int sq){return board[sq];}
    public boolean whiteToMove(){return white;}
    public int halfmoves(){return halfmove;}
    public boolean bareKing(boolean color){for(char p:board)if(p!=0&&side(p)==color&&Character.toLowerCase(p)!='k')return false;return true;}
    public static String square(int i){return ""+(char)('a'+i%8)+(char)('1'+i/8);}
    public static int index(String s){if(!s.matches("[a-h][1-8]"))throw new IllegalArgumentException("Ungültiges Feld");return s.charAt(0)-'a'+8*(s.charAt(1)-'1');}
    private static boolean side(char p){return Character.isUpperCase(p);}
    private static boolean inside(int f,int r){return f>=0&&f<8&&r>=0&&r<8;}
    public boolean inCheck(){return inCheck(white);}
    public boolean inCheck(boolean color){for(int i=0;i<64;i++)if(board[i]==(color?'K':'k'))return attacked(i,!color);throw new IllegalStateException("König fehlt");}
    public boolean attacked(int sq,boolean byWhite){
        int f=sq%8,r=sq/8;
        for(int i=0;i<64;i++){char p=board[i];if(p==0||side(p)!=byWhite)continue;int df=f-i%8,dr=r-i/8;char t=Character.toLowerCase(p);
            if(t=='p'&&Math.abs(df)==1&&dr==(byWhite?1:-1))return true;
            if(t=='n'&&Math.abs(df)*Math.abs(dr)==2)return true;
            if(t=='k'&&Math.max(Math.abs(df),Math.abs(dr))==1)return true;
            boolean diagonal=Math.abs(df)==Math.abs(dr)&&df!=0,straight=(df==0) != (dr==0);
            if((t=='b'&&diagonal)||(t=='r'&&straight)||(t=='q'&&(diagonal||straight))){
                int sf=Integer.signum(df),sr=Integer.signum(dr),x=i%8+sf,y=i/8+sr;boolean clear=true;
                while(x!=f||y!=r){if(board[y*8+x]!=0){clear=false;break;}x+=sf;y+=sr;}if(clear)return true;
            }
        }return false;
    }
    public List<Move> legalMoves(){
        List<Move> result=new ArrayList<>();for(Move m:pseudo()){MpsqChessRules next=new MpsqChessRules(this);next.apply(m);if(!next.inCheck(white))result.add(m);}return result;
    }
    public MpsqChessRules play(Move m){if(!legalMoves().contains(m))throw new IllegalArgumentException("Dieser Zug ist nicht erlaubt.");return after(m);}
    public MpsqChessRules play(String uci){return play(legalMoves().stream().filter(m->m.uci().equals(uci)).findFirst().orElseThrow(()->new IllegalArgumentException("Ungültiger Zug: "+uci)));}
    private MpsqChessRules after(Move m){MpsqChessRules n=new MpsqChessRules(this);n.apply(m);return n;}
    public long perft(int depth){if(depth==0)return 1;long n=0;for(Move m:legalMoves())n+=after(m).perft(depth-1);return n;}
    private void add(List<Move> out,int from,int to){if(Character.toLowerCase(board[from])=='p'&&(to/8==0||to/8==7))for(char p:new char[]{'q','r','b','n'})out.add(new Move(from,to,p));else out.add(new Move(from,to,(char)0));}
    private List<Move> pseudo(){
        List<Move> out=new ArrayList<>();
        for(int i=0;i<64;i++){char p=board[i];if(p==0||side(p)!=white)continue;int f=i%8,r=i/8;char t=Character.toLowerCase(p);
            if(t=='p'){
                int step=white?1:-1,nr=r+step;if(!inside(f,nr))continue;
                if(board[nr*8+f]==0){add(out,i,nr*8+f);if(r==(white?1:6)&&board[(r+2*step)*8+f]==0)add(out,i,(r+2*step)*8+f);}
                for(int df:new int[]{-1,1})if(inside(f+df,nr)){int to=nr*8+f+df;char target=board[to];if((target!=0&&side(target)!=white&&Character.toLowerCase(target)!='k')||(to==ep&&board[to-step*8]==(white?'p':'P')))add(out,i,to);}
            }else{
                int[][] dirs=t=='n'?new int[][]{{1,2},{2,1},{2,-1},{1,-2},{-1,-2},{-2,-1},{-2,1},{-1,2}}:new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
                for(int[] d:dirs){boolean diagonal=d[0]!=0&&d[1]!=0;if(t=='b'&&!diagonal||t=='r'&&diagonal)continue;
                    int x=f+d[0],y=r+d[1];while(inside(x,y)){int to=y*8+x;char target=board[to];if(target==0||side(target)!=white&&Character.toLowerCase(target)!='k')add(out,i,to);if(target!=0||t=='k'||t=='n')break;x+=d[0];y+=d[1];}
                }
                if(t=='k'&&i==(white?4:60)&&!inCheck())for(boolean kingSide:new boolean[]{true,false}){
                    char right=white?(kingSide?'K':'Q'):(kingSide?'k':'q');int rook=white?(kingSide?7:0):(kingSide?63:56),step=kingSide?1:-1;
                    if(rights.indexOf(right)<0||board[rook]!=(white?'R':'r'))continue;
                    boolean clear=true;for(int j=i+step;j!=rook;j+=step)if(board[j]!=0)clear=false;
                    if(clear&&!attacked(i+step,!white)&&!attacked(i+2*step,!white))add(out,i,i+2*step);
                }
            }
        }return out;
    }
    public boolean castling(Move m){return Character.toLowerCase(board[m.from])=='k'&&Math.abs(m.to-m.from)==2;}
    /** First friendly blocker along each geometrical move direction (never a legal move). */
    public List<Integer> friendlyBlockers(int from){
        List<Integer> out=new ArrayList<>();char piece=board[from];if(piece==0)return out;
        char type=Character.toLowerCase(piece);int f=from%8,r=from/8;
        int[][] dirs=type=='n'?new int[][]{{1,2},{2,1},{2,-1},{1,-2},{-1,-2},{-2,-1},{-2,1},{-1,2}}:type=='p'?new int[][]{{0,side(piece)?1:-1}}:new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
        for(var d:dirs){boolean diagonal=d[0]!=0&&d[1]!=0;if(type=='b'&&!diagonal||type=='r'&&diagonal)continue;int max=type=='n'||type=='k'?1:type=='p'?(r==(side(piece)?1:6)?2:1):7;
            for(int n=1;n<=max;n++){int x=f+d[0]*n,y=r+d[1]*n;if(!inside(x,y))break;char target=board[y*8+x];if(target!=0){if(side(target)==side(piece))out.add(y*8+x);break;}}
        }return out;
    }
    public int capturedSquare(Move m){return Character.toLowerCase(board[m.from])=='p'&&m.to==ep&&board[m.to]==0?m.to+(white?-8:8):m.to;}
    private void apply(Move m){
        char p=board[m.from],captured=board[capturedSquare(m)];boolean pawn=Character.toLowerCase(p)=='p';
        if(pawn&&m.to==ep&&board[m.to]==0)board[capturedSquare(m)]=0;
        if(castling(m)){int rook=m.to>m.from?m.from+3:m.from-4;board[(m.from+m.to)/2]=board[rook];board[rook]=0;}
        board[m.from]=0;board[m.to]=m.promotion==0?p:(white?Character.toUpperCase(m.promotion):m.promotion);
        if(p=='K')rights=rights.replace("K","").replace("Q","");if(p=='k')rights=rights.replace("k","").replace("q","");
        for(int i=0;i<4;i++){int sq=new int[]{0,7,56,63}[i];if(m.from==sq||m.to==sq)rights=rights.replace(String.valueOf("QKqk".charAt(i)),"");}
        ep=pawn&&Math.abs(m.to-m.from)==16?(m.to+m.from)/2:-1;halfmove=pawn||captured!=0?0:halfmove+1;if(!white)fullmove++;white=!white;
    }
    public String fen(){
        StringBuilder s=new StringBuilder();for(int r=7;r>=0;r--){int empty=0;for(int f=0;f<8;f++){char p=board[r*8+f];if(p==0)empty++;else{if(empty>0)s.append(empty);empty=0;s.append(p);}}if(empty>0)s.append(empty);if(r>0)s.append('/');}
        return s+" "+(white?"w":"b")+" "+(rights.isEmpty()?"-":rights)+" "+(ep<0?"-":square(ep))+" "+halfmove+" "+fullmove;
    }
    /** En-passant matters for repetition only when a legal capture exists. */
    public String repetitionKey(){String[] p=fen().split(" ");boolean usable=ep>=0&&legalMoves().stream().anyMatch(m->m.to==ep&&Character.toLowerCase(board[m.from])=='p'&&m.from%8!=m.to%8);return p[0]+" "+p[1]+" "+p[2]+" "+(usable?p[3]:"-");}
    public boolean insufficientMaterial(){
        int minors=0,bishops=0,complex=-1;for(int i=0;i<64;i++){char t=Character.toLowerCase(board[i]);if(t==0||t=='k')continue;if(t!='n'&&t!='b')return false;minors++;if(t=='b'){bishops++;int c=(i%8+i/8)%2;if(complex==-1)complex=c;else if(complex!=c)complex=2;}}
        return minors<=1||minors==bishops&&complex<2;
    }
}
