package cz.dachman.drone.director;

/** A one-use approval bound to a specific revision, invalidated on stop/change. */
public final class ApprovalGate {
    private long revision = 0;
    private String pending;
    public long request(String maneuver) { pending = maneuver; return ++revision; }
    public void cancel() { pending = null; revision++; }
    public String pending() { return pending; }
    public long revision() { return revision; }
    public boolean approve(long challenge, boolean authenticated) {
        if (!authenticated || pending == null || challenge != revision) return false;
        cancel();
        return true;
    }
}
