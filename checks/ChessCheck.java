import de.galaxushd.mpsqcamera.MpsqChessRules;
import de.galaxushd.mpsqcamera.MpsqChessGame;

public class ChessCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static boolean has(MpsqChessRules p,String move){return p.legalMoves().stream().anyMatch(m->m.uci().equals(move));}
    static void move(MpsqChessGame g,String u){g.move(g.position().legalMoves().stream().filter(m->m.uci().equals(u)).findFirst().orElseThrow(()->new AssertionError(u)));}
    static MpsqChessGame session(String fen){var g=new MpsqChessGame(fen);g.join(true);g.join(false);return g;}
    public static void main(String[] args){
        MpsqChessRules start=new MpsqChessRules();
        long[] expected={1,20,400,8902,197281};for(int i=1;i<expected.length;i++)check(start.perft(i)==expected[i],"Start perft "+i);
        MpsqChessRules kiwi=new MpsqChessRules("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        check(kiwi.perft(1)==48,"Kiwipete 1");check(kiwi.perft(2)==2039,"Kiwipete 2");check(kiwi.perft(3)==97862,"Kiwipete 3");
        MpsqChessRules end=new MpsqChessRules("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1");check(end.perft(3)==2812,"EP/endgame perft");
        MpsqChessRules ep=new MpsqChessRules("k7/8/8/r4pPK/8/8/8/8 w - f6 0 1");check(!has(ep,"g5f6"),"Pinned en passant forbidden");
        MpsqChessRules castle=new MpsqChessRules("4kr2/8/8/8/8/8/8/4K2R w K - 0 1");check(!has(castle,"e1g1"),"Castle through check forbidden");
        MpsqChessRules promo=new MpsqChessRules("7k/P7/8/8/8/8/8/7K w - - 0 1");for(String p:new String[]{"q","r","b","n"})check(has(promo,"a7a8"+p),"Promotion "+p);
        MpsqChessRules mate=start.play("f2f3").play("e7e5").play("g2g4").play("d8h4");check(mate.inCheck()&&mate.legalMoves().isEmpty(),"Fools mate");
        MpsqChessRules stalemate=new MpsqChessRules("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1");check(!stalemate.inCheck()&&stalemate.legalMoves().isEmpty(),"Stalemate");
        check(start.play("e2e4").repetitionKey().endsWith(" -"),"Unusable EP not repetition-relevant");
        check(new MpsqChessRules(start.fen()).fen().equals(start.fen()),"FEN roundtrip");
        MpsqChessGame game=new MpsqChessGame();game.join(true);check(!game.active(),"Wait for black");game.join(false);check(game.active(),"Both seats start");
        for(String u:new String[]{"f2f3","e7e5","g2g4","d8h4"})game.move(game.position().legalMoves().stream().filter(m->m.uci().equals(u)).findFirst().orElseThrow());
        check(game.result()==MpsqChessGame.Result.CHECKMATE&&!game.winner(),"Black wins");check(!game.active()&&!game.joined(true)&&!game.joined(false),"End removes both seats");
        check(game.position().fen().equals(MpsqChessRules.START),"End resets board");game.join(true);check(!game.active(),"Rematch needs both seats again");game.reset();check(!game.joined(true),"Reset clears waiting seat");
        for(String promotion:new String[]{"r","q"}){
            var trophies=session("7k/1P6/8/8/8/r7/R7/7K b - - 0 1");move(trophies,"a3a2");check(trophies.trophies().equals(java.util.List.of('R')),"Captured white rook trophy");move(trophies,"b7b8"+promotion);check(trophies.trophies().size()==(promotion.equals("r")?0:1),"Promotion consumes only matching own trophy");
        }
        var patt=session("7k/5K2/8/6Q1/8/8/8/8 w - - 0 1");move(patt,"g5g6");check(patt.result()==MpsqChessGame.Result.STALEMATE&&!patt.active()&&!patt.joined(true)&&!patt.joined(false),"Patt ends and clears seats");
        var repetition=session(MpsqChessRules.START);for(int cycle=0;cycle<4;cycle++)for(String m:new String[]{"g1f3","g8f6","f3g1","f6g8"})move(repetition,m);check(repetition.result()==MpsqChessGame.Result.REPETITION&&!repetition.active(),"Fivefold automatic draw");
        var seventyFive=session("7k/8/8/8/8/8/R7/7K w - - 149 76");move(seventyFive,"a2a3");check(seventyFive.result()==MpsqChessGame.Result.SEVENTY_FIVE,"75 move automatic draw");
        var precedence=session("7k/5K2/8/6Q1/8/8/8/8 w - - 149 76");move(precedence,"g5h5");check(precedence.result()==MpsqChessGame.Result.CHECKMATE,"Mate takes precedence over 75 move rule");
        var castled=new MpsqChessRules("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1").play("e1c1");check(castled.piece(2)=='K'&&castled.piece(3)=='R'&&castled.piece(0)==0,"Rook moves with castling");
        var capturedEp=new MpsqChessRules("7k/8/8/3pP3/8/8/8/K7 w - d6 0 1").play("e5d6");check(capturedEp.piece(MpsqChessRules.index("d5"))==0,"EP removes pawn behind target");
        System.out.println("Chess checks passed: perft, king safety, castling, en passant, promotion, mate, stalemate, reset/rematch.");
    }
}
