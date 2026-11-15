from pathlib import Path
p=Path('src/client/java/de/galaxushd/mpsqcamera/MpsqAccessoryRenderer.java');s=p.read_text(encoding='utf-8')
s=s.replace('    private record HeadAngleHistory(HeadAngles previous,HeadAngles current){}\n','').replace('    private static final Map<UUID,HeadAngleHistory> playerHeadAngles=new HashMap<>();\n','').replace('            updatePlayerHeadAngles(client);\n','')
a=s.index('    private static void updatePlayerHeadAngles(');z=s.index('    private static HeadAngles interpolatedHeadAngles',a);s=s[:a]+s[z:];p.write_text(s,encoding='utf-8')
