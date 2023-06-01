package io.github.benkoff.webrtcss.domain;

import org.springframework.web.socket.WebSocketSession;

import javax.validation.constraints.NotNull;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Room
{
    @NotNull private final Long id;
    // sockets by user names
    private final Map<String, WebSocketSession> clients = new HashMap<>();
    // Qing
    private String initiator = null;
    private String sdpInitiator = null;
    //
    public Room(Long id)
    {
        this.id = id;
    }
    public Long getId()
    {
        return id;
    }
    Map<String, WebSocketSession> getClients()
    {
        return clients;
    }    
    @Override
    public boolean equals(final Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Room room = (Room) o;
        return Objects.equals(getId(), room.getId()) &&
                Objects.equals(getClients(), room.getClients());
    }
    @Override
    public int hashCode()
    {
        return Objects.hash(getId(), getClients());
    }
    // Qing
    public void setInitiator(String _initiator)
    {
        initiator = _initiator;
    }
    public String getInitiator()
    {
        return initiator;
    }
    public void setInitiatorSDP(String _sdpInitiator)
    {
        sdpInitiator = _sdpInitiator;
    }
    public String getInitiatorSDP()
    {
        return sdpInitiator;
    }
    public void removeClient(WebSocketSession session)
    {
        boolean fRemoving = false;
        String removedUser = null;
        //
        Set<String> users = clients.keySet();
        Iterator<String> it = users.iterator();
        while( it.hasNext() )
        {
            String user = it.next();;
            WebSocketSession _session = clients.get(user);
            if( session == _session )
            {
                fRemoving = true;
                removedUser = user;
            }
        }
        clients.remove(removedUser);
        //
        if( clients.size() <= 0 )
            sdpInitiator = null;
    }
}
