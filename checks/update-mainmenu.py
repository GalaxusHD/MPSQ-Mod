from pathlib import Path
p=Path('src/client/java/de/galaxushd/mpsqcamera');f=p/'ModConfigScreen.java';s=f.read_text(encoding='utf-8');s=s.replace('    private EditBox codeInputField;','    private final String page;\n    private final Screen parent;\n    private EditBox codeInputField;');s=s.replace('public ModConfigScreen() { super(Component.translatable("gui.mpsqcamera.main.title")); }','''public ModConfigScreen() { this("",null); }
    private ModConfigScreen(String page,Screen parent) { super(page.isEmpty()?Component.translatable("gui.mpsqcamera.main.title"):Component.literal(page));this.page=page;this.parent=parent; }
    private void menuButton(String label,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,w,20).build());}
    private void section(String label){minecraft.gui.setScreen(new ModConfigScreen(label,this));}''')
a=s.index('        codeInputField = new EditBox');b=s.index('\n    private void refreshRoleForMenu()',a)
s=s[:a]+'''        codeInputField=null;joinButton=null;
        TeamRank base=TeamStateStore.self().map(TeamProfile::baseRank).orElse(TeamRank.PLAYER);
        boolean officer=base.level()>=TeamRank.OFFICER.level();
        boolean manage=TeamStateStore.self().map(p->p.permissionRank().level()>=TeamRank.OFFICER.level()).orElse(false);
        if(!page.isEmpty()){
            int y=45;
            switch(page){
                case "Kino" -> {
                    codeInputField=addRenderableWidget(new EditBox(font,menuX,y,buttonWidth,20,Component.translatable("gui.mpsqcamera.main.activation.placeholder")));
                    codeInputField.setMaxLength(6);codeInputField.setResponder(code->updateActivationCodeState());setInitialFocus(codeInputField);
                    joinButton=addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.main.join"),b->submitCode()).bounds(menuX,y+26,buttonWidth,20).build());
                    menuButton("Bildschirme",menuX,y+52,buttonWidth,this::openScreens);updateActivationCodeState();
                }
                case "Bearbeiten" -> {
                    if(manage){menuButton("System",menuX,y,buttonWidth,()->minecraft.gui.setScreen(new MpsqActionSetupScreen()));y+=26;
                        menuButton("Models",menuX,y,buttonWidth,()->minecraft.gui.setScreen(new MpsqModelsScreen(this)));y+=26;}
                    menuButton("Bildschirme",menuX,y,buttonWidth,this::openScreens);y+=26;
                    if(TeamStateStore.self().map(TeamProfile::canViewCameras).orElse(false))menuButton("Kameras",menuX,y,buttonWidth,this::openCameras);
                }
                case "Team" -> {
                    if(officer||base.level()<TeamRank.SOLDIER.level()){menuButton("To-dos",menuX,y,buttonWidth,this::openTodo);y+=26;}
                    if(officer){menuButton("Texte",menuX,y,buttonWidth,this::openTemplates);y+=26;
                        menuButton("Logs",menuX,y,buttonWidth,()->minecraft.gui.setScreen(new TeamLogsScreen(this)));y+=26;
                        menuButton("Replay",menuX,y,buttonWidth,()->minecraft.gui.setScreen(new MpsqReplayScreen(this)));}
                }
            }
            menuButton("Zurück",menuX,height-28,buttonWidth,this::onClose);return;
        }
        menuButton("Kino",menuX,nextControlY(menuTop,0),buttonWidth,()->section("Kino"));
        menuButton("Sammlung",menuX,nextControlY(menuTop,1),buttonWidth,()->minecraft.gui.setScreen(new MpsqCollectionScreen(this)));
        menuButton("Team",menuX,nextControlY(menuTop,2),buttonWidth,()->section("Team"));
        menuButton("Bearbeiten",menuX,nextControlY(menuTop,3),buttonWidth,()->section("Bearbeiten"));
        menuButton("Einstellungen",menuX,nextControlY(menuTop,4),buttonWidth,this::openSettings);
        menuButton("Lizenz",LICENSE_MARGIN,height-LICENSE_MARGIN-20,LICENSE_WIDTH,this::openLicense);
        menuButton("Ränge",LICENSE_MARGIN,height-LICENSE_MARGIN-46,LICENSE_WIDTH,this::openMembers);
        addRenderableWidget(Button.builder(visibilityButtonText(),button->{TeamVisibilitySettings.toggle();minecraft.gui.setScreen(new ModConfigScreen());})
            .bounds(width-LICENSE_MARGIN-LICENSE_WIDTH,height-LICENSE_MARGIN-20,LICENSE_WIDTH,20).build());
        if(TeamVisibilitySettings.visible())addRenderableWidget(Button.builder(nameVisibilityButtonText(),this::toggleOwnNameVisibility)
            .bounds(width-LICENSE_MARGIN-LICENSE_WIDTH,height-LICENSE_MARGIN-46,LICENSE_WIDTH,20).build());
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
''' +s[b:]
s=s.replace('        boolean hasFocus = codeInputField.isFocused();','        if(codeInputField==null)return;\n        boolean hasFocus = codeInputField.isFocused();');s=s.replace('        String rankError = MpsqApiClient.teamProfileError();','        if(!page.isEmpty()){context.centeredText(font,title,width/2,20,0xFFFFFFFF);return;}\n        String rankError = MpsqApiClient.teamProfileError();');f.write_text(s,encoding='utf-8')
f=p/'CinemaAudioManager.java';s=f.read_text(encoding='utf-8').replace('case CINEMA -> ModConfig.volume;','case CINEMA -> options.getSoundSourceVolume(SoundSource.BLOCKS);');s=s.replace('        execute(browser, "(()=>{const apply=d=>{d.querySelectorAll', '''        execute(browser, "(()=>{const apply=d=>{d.querySelectorAll''')
s=s.replace('if(f.contentDocument)apply(f.contentDocument)}catch(e){}','if(f.contentDocument)apply(f.contentDocument);const u=new URL(f.src);if(u.hostname===\'www.youtube.com\'||u.hostname===\'www.youtube-nocookie.com\')f.contentWindow.postMessage(JSON.stringify({event:\'command\',func:\'setVolume\',args:["+volume+"*100]}),u.origin)}catch(e){}')
f.write_text(s,encoding='utf-8')
f=p/'MpsqAudioManager.java';s=f.read_text(encoding='utf-8').replace('SoundSource.BLOCKS','SoundSource.AMBIENT').replace('return id != null && id.getPath().startsWith("entity.") ? SoundSource.NEUTRAL : SoundSource.AMBIENT;','return SoundSource.AMBIENT;');f.write_text(s,encoding='utf-8')
