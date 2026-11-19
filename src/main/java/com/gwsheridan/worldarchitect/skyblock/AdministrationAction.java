package com.gwsheridan.worldarchitect.skyblock;

import java.util.*;
import net.minecraft.resources.Identifier;

/** A narrow command grammar keeps panel actions on the existing permissioned command paths. */
public record AdministrationAction(String command,String description) {
    public static AdministrationAction parse(String text,Set<String> online){
        if(text.length()>512||text.indexOf('\n')>=0||text.indexOf('\r')>=0)throw new IllegalArgumentException("Invalid administration action.");
        String command=text.strip();String[] t=command.split(" ",-1);if(Arrays.stream(t).anyMatch(String::isEmpty))throw new IllegalArgumentException("Use single spaces between action fields.");
        String description;
        if(t.length>=2&&t[0].equals("wa_island")){
            switch(t[1]){
                case "reassign"->{count(t,4);player(t[2]);slot(t[3]);description="Reassign "+t[2]+" to island "+t[3]+".";}
                case "swap"->{count(t,4);player(t[2]);player(t[3]);description="Swap island assignments for "+t[2]+" and "+t[3]+".";}
                case "team"->{count(t,5);if(!t[2].equals("assign"))throw invalid();team(t[3]);slot(t[4]);description="Assign team "+t[3]+" to island "+t[4]+".";}
                case "protection"->{
                    if(t.length==4&&List.of("breaking","placement","containers","shared").contains(t[2])){bool(t[3]);description="Set "+t[2]+" protection to "+t[3]+".";}
                    else{count(t,5);if(!t[2].equals("container")||!List.of("add","remove").contains(t[3]))throw invalid();id(t[4]);description=t[3]+" protected container type "+t[4]+".";}
                }
                default->throw invalid();
            }
        }else if(t.length>=3&&t[0].equals("wa_spawn")){
            if(!List.of("initial","respawn","portal").contains(t[2]))throw invalid();
            if(t[1].equals("clear")){if(t.length!=3&&t.length!=4)throw invalid();if(t.length==4)id(t[3]);description="Clear the "+t[2]+" spawn override"+(t.length==4?" for "+t[3]:"")+".";}
            else{
                if(!t[1].equals("set")||t.length<5)throw invalid();id(t[4]);
                switch(t[3]){
                    case "island","team"->count(t,5);
                    case "fixed"->{count(t,8);position(t,5);}
                    case "marker"->{if(t.length<10||t.length>12)throw invalid();id(t[5]);marker(t[6]);position(t,7);if(t.length>10&&!List.of("none","cw90","cw180","ccw90").contains(t[10]))throw invalid();if(t.length>11&&!List.of("none","left_right","front_back").contains(t[11]))throw invalid();}
                    case "generated"->{count(t,11);id(t[5]);position(t,6);marker(t[9]);marker(t[10]);}
                    default->throw invalid();
                }
                description="Set "+t[2]+" spawn to "+String.join(" ",Arrays.copyOfRange(t,3,t.length))+".";
            }
        }else if(t.length>=3&&t[0].equals("team")){
            switch(t[1]){
                case "add"->{count(t,3);team(t[2]);description="Create scoreboard team "+t[2]+".";}
                case "join"->{count(t,4);team(t[2]);online(t[3],online);description="Add "+t[3]+" to team "+t[2]+".";}
                case "leave"->{count(t,3);online(t[2],online);description="Remove "+t[2]+" from their current team.";}
                default->throw invalid();
            }
        }else throw invalid();
        return new AdministrationAction(command,description);
    }
    private static void count(String[] fields,int count){if(fields.length!=count)throw invalid();}
    private static void slot(String value){int n=Integer.parseInt(value);if(n<1||n>4095)throw new IllegalArgumentException("Choose a personal island slot from 1 to 4095.");}
    private static void player(String value){if(!value.matches("[A-Za-z0-9_]{1,16}")){try{UUID.fromString(value);}catch(IllegalArgumentException e){throw new IllegalArgumentException("Choose an online player name or saved player UUID.");}}}
    private static void team(String value){if(value.isBlank()||value.length()>64||value.codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isSpaceChar(c)||Character.isISOControl(c)))throw new IllegalArgumentException("Choose a valid scoreboard team name.");}
    private static void marker(String value){if(!value.matches("[a-z0-9_-]{1,32}"))throw new IllegalArgumentException("Choose a lowercase module or marker name.");}
    private static void online(String value,Set<String> online){if(!online.contains(value))throw new IllegalArgumentException("Team membership changes require an online player name.");}
    private static void id(String value){try{Identifier.parse(value);}catch(RuntimeException e){throw new IllegalArgumentException("Choose a valid resource ID.",e);}}
    private static void bool(String value){if(!value.equals("true")&&!value.equals("false"))throw invalid();}
    private static void position(String[] values,int offset){for(int i=0;i<3;i++){long n=Long.parseLong(values[offset+i]);long bound=i==1?2031:29_999_983;if(n>(bound)||n<(i==1?-2032:-bound))throw new IllegalArgumentException("Spawn coordinates exceed the supported bounds.");}}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Choose an island, team, protection or spawn action supported by the panel.");}
}
