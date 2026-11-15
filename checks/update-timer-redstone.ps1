$base='src/client/java/de/galaxushd/mpsqcamera/'
function Edit($file,$old,$new){$p=Join-Path (Get-Location) ($base+$file+'.java');$s=[IO.File]::ReadAllText($p);if(!$s.Contains($old)){throw "Missing $file : $old"};[IO.File]::WriteAllText($p,$s.Replace($old,$new))}
Edit 'MpsqActionSetupScreen' 'private Button playlistButton;' 'private Button playlistButton,timerButton;private int timerMode;'
Edit 'MpsqActionSetupScreen' '        barColorButton=addRenderableWidget' '        timerButton=addRenderableWidget(Button.builder(Component.literal(timerLabel()),b->{timerMode=(timerMode+1)%4;timerButton.setMessage(Component.literal(timerLabel()));updateVisibility();}).bounds(x,y+49,260,20).build());
        barColorButton=addRenderableWidget'
Edit 'MpsqActionSetupScreen' '        duration.visible=countdown||rlglGreen;duration.active=countdown||rlglGreen;' '        timerButton.visible=countdown;timerButton.active=countdown;duration.visible=(countdown&&timerMode==0)||rlglGreen;duration.active=duration.visible;'
Edit 'MpsqActionSetupScreen' 'barColorButton.visible=countdown||bossbar;barColorButton.active=countdown||bossbar;' 'barColorButton.visible=(countdown&&timerMode==0)||bossbar;barColorButton.active=barColorButton.visible;'
Edit 'MpsqActionSetupScreen' 'selected.equals("START_CINEMATIC")?"Name der Kamerafahrt"' 'countdown&&timerMode>0?"Name des ModularTimers":selected.equals("START_CINEMATIC")?"Name der Kamerafahrt"'
Edit 'MpsqActionSetupScreen' '            case "TOGGLE_COUNTDOWN" -> {' '            case "TOGGLE_COUNTDOWN" -> {
                if(timerMode>0){try{MpsqModularTimer.requireEdit();MpsqModularTimer.find(value.getValue().trim());}catch(Exception e){status=e.getMessage();return;}data.addProperty("timerName",value.getValue().trim());data.addProperty("timerAction",timerMode==1?"start":timerMode==2?"pause":"toggle");break;}'
Edit 'MpsqActionSetupScreen' '    private String screenLabel(){' '    private String timerLabel(){return switch(timerMode){case 1->"ModularTimer starten";case 2->"ModularTimer stoppen";case 3->"ModularTimer umschalten";default->"Countdown-Bossbar";};}
    private String screenLabel(){'
Edit 'MpsqActionSync' 'case "TOGGLE_COUNTDOWN" -> {boolean running=' 'case "TOGGLE_COUNTDOWN" -> {if(data.has("timerName")){MpsqModularTimer.redstone(data.get("timerName").getAsString(),data.get("timerAction").getAsString(),stateDriven,powered);break;}boolean running='
Edit 'MpsqModularTimer' '    static void initialize(){' '    static void redstone(String name,String action,boolean stateDriven,boolean powered){if(!editable())return;try{Timer t=find(name);String chosen=stateDriven?(powered?"start":"pause"):action.equals("toggle")?(t.clock.running?"pause":"start"):action;change(t,chosen,0);}catch(IllegalArgumentException e){MpsqCameraClient.LOGGER.warn("Timer-Redstone: {}",e.getMessage());}}
    static void initialize(){'
