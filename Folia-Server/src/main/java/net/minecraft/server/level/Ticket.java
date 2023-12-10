package net.minecraft.server.level;

import java.util.Objects;

public final class Ticket<T> implements Comparable<Ticket<?>> {
    private final TicketType<T> type;
    private final int ticketLevel;
    public final T key;
    // Paper start - rewrite chunk system
    public long removeDelay;

    public Ticket(TicketType<T> type, int level, T argument, long removeDelay) {
        this.removeDelay = removeDelay;
        // Paper end - rewrite chunk system
        this.type = type;
        this.ticketLevel = level;
        this.key = argument;
    }

    @Override
    public int compareTo(Ticket<?> ticket) {
        int i = Integer.compare(this.ticketLevel, ticket.ticketLevel);
        if (i != 0) {
            return i;
        } else {
            int j = Integer.compare(System.identityHashCode(this.type), System.identityHashCode(ticket.type));
            return j != 0 ? j : this.type.getComparator().compare(this.key, (T)ticket.key); // Paper - decompile fix
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (!(object instanceof Ticket)) {
            return false;
        } else {
            Ticket<?> ticket = (Ticket)object;
            return this.ticketLevel == ticket.ticketLevel && Objects.equals(this.type, ticket.type) && Objects.equals(this.key, ticket.key);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.type, this.ticketLevel, this.key);
    }

    @Override
    public String toString() {
        return "Ticket[" + this.type + " " + this.ticketLevel + " (" + this.key + ")] to die in " + this.removeDelay; // Paper - rewrite chunk system
    }

    public TicketType<T> getType() {
        return this.type;
    }

    public int getTicketLevel() {
        return this.ticketLevel;
    }

    protected void setCreatedTick(long tickCreated) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    protected boolean timedOut(long currentTick) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }
}
