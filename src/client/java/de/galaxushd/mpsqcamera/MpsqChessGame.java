package de.galaxushd.mpsqcamera;

import java.util.*;

/** Local two-seat session. Presentation may retain a result, but seats always expire. */
public final class MpsqChessGame {
    public enum Result { PLAYING, CHECKMATE, STALEMATE, MATERIAL, REPETITION, SEVENTY_FIVE, RESIGNED, TIME }
    private final String initialFen;
    private MpsqChessRules position;
    public MpsqChessGame(){this(MpsqChessRules.START);}
    public MpsqChessGame(String fen){position=new MpsqChessRules(fen);initialFen=position.fen();}
    private final List<Character> trophies=new ArrayList<>();
    private final Map<String,Integer> repeats=new HashMap<>();
    private boolean whiteSeat,blackSeat,active;
    private Result result=Result.PLAYING;
    private Boolean winner;
    public MpsqChessRules position(){return position;}
    public List<Character> trophies(){return List.copyOf(trophies);}
    public boolean active(){return active;}
    public boolean joined(boolean white){return white?whiteSeat:blackSeat;}
    public Result result(){return result;}
    public Boolean winner(){return winner;}
    public void join(boolean white){
        if(active)throw new IllegalStateException("Partie läuft bereits.");
        if(white)whiteSeat=true;else blackSeat=true;
        if(whiteSeat&&blackSeat){position=new MpsqChessRules(initialFen);trophies.clear();repeats.clear();repeats.put(position.repetitionKey(),1);active=true;result=Result.PLAYING;winner=null;}
    }
    public void reset(){result=Result.PLAYING;winner=null;active=false;whiteSeat=blackSeat=false;position=new MpsqChessRules(initialFen);trophies.clear();repeats.clear();}
    public void finish(Result reason,Boolean winningWhite){reset();result=reason;winner=winningWhite;}
    public void move(MpsqChessRules.Move move){
        if(!active)throw new IllegalStateException("Zuerst beiden Farben beitreten.");
        MpsqChessRules next=position.play(move);char captured=position.piece(position.capturedSquare(move));
        if(captured!=0)trophies.add(captured);
        if(move.promotion()!=0)trophies.remove(Character.valueOf(position.whiteToMove()?Character.toUpperCase(move.promotion()):move.promotion()));
        position=next;int repetition=repeats.merge(position.repetitionKey(),1,Integer::sum);
        if(position.legalMoves().isEmpty()){boolean check=position.inCheck();finish(check?Result.CHECKMATE:Result.STALEMATE,check?!position.whiteToMove():null);}
        else if(position.insufficientMaterial())finish(Result.MATERIAL,null);
        else if(repetition>=5)finish(Result.REPETITION,null);
        else if(position.halfmoves()>=150)finish(Result.SEVENTY_FIVE,null);
    }
}
