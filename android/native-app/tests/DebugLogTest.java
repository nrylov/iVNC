/* Local tests exercise the actual bounded diagnostic buffer, independent of Android UI. */
package dev.ivnc.nativeclient;

/** Checks that a long connection history keeps useful recent events without accepting payload kinds. */
public final class DebugLogTest {
    public static void main(String[] args){
        DebugLog log=new DebugLog("synthetic metadata");
        log.add("status","first connection");log.add("error","specific failure");
        log.add("status","Native connection ended");
        require(log.snapshot().contains("specific failure"),"Cleanup removed the error");
        log.add("clipboard","synthetic-private-text");log.add("credentials","synthetic-private-password");
        require(!log.snapshot().contains("synthetic-private"),"Unapproved payload kind was logged");
        for(int i=0;i<300;i++)log.add("protocol","event-"+i);
        require(!log.snapshot().contains("first connection")&&log.snapshot().contains("event-299"),"Bounded history did not retain recent events");
        log.add("status","x".repeat(5000));require(log.snapshot().length()<20000,"One event exceeded its text bound");
        log.clear();require(!log.snapshot().contains("event-299")&&log.snapshot().contains("synthetic metadata"),"Clear lost metadata or retained events");
        System.out.println("PASS: debug log retains stages/errors, bounds entries/text, excludes payload kinds, and clears safely");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
