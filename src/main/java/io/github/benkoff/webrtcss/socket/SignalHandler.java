package io.github.benkoff.webrtcss.socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.benkoff.webrtcss.domain.Room;
import io.github.benkoff.webrtcss.domain.RoomService;
import io.github.benkoff.webrtcss.domain.WebSocketMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
public class SignalHandler extends TextWebSocketHandler
{
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ObjectMapper objectMapper = new ObjectMapper();
   @Autowired private RoomService roomService;
    //
    private Map<String, Room> sessionIdToRoomMap = new HashMap<>();
    //
    private static final String MSG_TYPE_TEXT = "text";
    private static final String MSG_TYPE_OFFER = "offer";
    private static final String MSG_TYPE_ANSWER = "answer";
    private static final String MSG_TYPE_ICE = "ice";
    private static final String MSG_TYPE_JOIN = "join";
    private static final String MSG_TYPE_LEAVE = "leave";
    //
    @Override
    public void afterConnectionClosed(final WebSocketSession session, final CloseStatus status)
    {
        logger.debug("[ws] Session has been closed with status {}", status);
        //
        Room room = sessionIdToRoomMap.get(session.getId());
        sessionIdToRoomMap.remove(session.getId());
        if( room != null )
            roomService.removeClient(room, session);
    }
    @Override
    public void afterConnectionEstablished(final WebSocketSession session)
    {
        String sdpInitiator = null;
        Room rm = sessionIdToRoomMap.get(session.getId());
        if (rm != null)
        {
            logger.debug("[ws] afterConnectionEstablished.room: ", rm.getId());
            logger.debug("[ws] afterConnectionEstablished.reply.sdpInitiator: ", sdpInitiator);
            sdpInitiator = rm.getInitiatorSDP();
        }
        else
        {
            logger.debug("[ws] afterConnectionEstablished.room.creatingNewOne");
        }
    }
    protected void handleJoin(final WebSocketSession session)
    {
        logger.debug("[ws] handleJoin.room: ", session.getId());
        //
        String sdpInitiator = null;
        Room rm = sessionIdToRoomMap.get(session.getId());
        if (rm != null)
        {
            logger.debug("[ws] handleJoin.room: ", rm.getId());
            logger.debug("[ws] handleJoin.reply.sdpInitiator: ", sdpInitiator);
            sdpInitiator = rm.getInitiatorSDP();
        }
        else
        {
            logger.debug("[ws] handleJoin.room.creatingNewOne");
        }
        //
        sendMessage(session, new WebSocketMessage("Server", MSG_TYPE_JOIN, Boolean.toString(!sessionIdToRoomMap.isEmpty()), null, sdpInitiator));
    }
    @Override
    protected void handleTextMessage(final WebSocketSession session, final TextMessage textMessage)
    {
        try
        {
            WebSocketMessage message = objectMapper.readValue(textMessage.getPayload(), WebSocketMessage.class);
            logger.debug("[ws] Message of {} type from {} received", message.getType(), message.getFrom());
            String userName = message.getFrom(); 
            String data = message.getData(); 
            //            
            Room room;
            switch (message.getType())
            {
                case MSG_TYPE_TEXT:
                    logger.debug("[ws] Text message: {}", message.getData());
                    break;
                case MSG_TYPE_OFFER:
                case MSG_TYPE_ANSWER:
                case MSG_TYPE_ICE:
                    Object candidate = message.getCandidate();
                    Object sdp = message.getSdp();
                    logger.debug("[ws] Signal: {}",
                            candidate != null
                                    ? candidate.toString().substring(0, 64)
                                    : sdp.toString().substring(0, 64));
                    Room rm = sessionIdToRoomMap.get(session.getId());
                    if (rm != null) {
                        // Qing
                        if( rm.getInitiatorSDP() == null )
                        {
                            logger.debug("[ws] Signal.initiator.setSDP: {}", sdp);
                            StringBuffer sb = new StringBuffer();
                            sb.append("{\"type\":\"offer\", \"sdp\":\"");
                            sb.append(sdp.toString());
                            sb.append("\"}");
                            String s = sb.toString().replaceAll("\r", "\\\\r");
                            s = s.replaceAll("\n", "\\\\n");
                            rm.setInitiatorSDP(s);
                            //
                            rm.setInitiator(message.getFrom());
                        }
                        else
                        {
                            logger.debug("[ws] Signal.non-initiator");
                        }
                        //
                        Map<String, WebSocketSession> clients = roomService.getClients(rm);
                        for(Map.Entry<String, WebSocketSession> client : clients.entrySet())  {
                            // send messages to all clients except current user
                            if (!client.getKey().equals(userName)) {
                                // select the same type to resend signal
                                sendMessage(client.getValue(),
                                        new WebSocketMessage(
                                                userName,
                                                message.getType(),
                                                data,
                                                candidate,
                                                sdp));
                            }
                        }
                    }
                    break;
                case MSG_TYPE_JOIN:
                    logger.debug("[ws] {} has joined Room: #{}", userName, message.getData());
                    room = roomService.findRoomByStringId(data)
                            .orElseThrow(() -> new IOException("Invalid room number received!"));
                    roomService.addClient(room, userName, session);
                    sessionIdToRoomMap.put(session.getId(), room);
                    //
                    handleJoin(session);
                    break;

                case MSG_TYPE_LEAVE:
                    logger.debug("[ws] {} is going to leave Room: #{}", userName, message.getData());
                    room = sessionIdToRoomMap.get(session.getId());
                    Optional<String> client = roomService.getClients(room).entrySet().stream()
                            .filter(entry -> Objects.equals(entry.getValue().getId(), session.getId()))
                            .map(Map.Entry::getKey)
                            .findAny();
                    client.ifPresent(c -> roomService.removeClientByName(room, c));
                    //
                    if( room.getInitiator() != null && room.getInitiator().equals(userName) )
                    {
                        logger.debug("[ws] Room initiator {} is leaving, close all sessions.", room.getInitiator());
                        room.setInitiator(null);
                        roomService.closeAllSession(room);
                    }
                    break;
                default:
                    logger.debug("[ws] Type of the received message {} is undefined!", message.getType());
            }

        }
        catch (IOException e)
        {
            logger.debug("An error occured: {}", e.getMessage());
        }
    }
    private void sendMessage(WebSocketSession session, WebSocketMessage message)
    {
        try
        {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        }
        catch (IOException e)
        {
            logger.debug("An error occured: {}", e.getMessage());
        }
    }
}
