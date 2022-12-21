package codes.monkey.reactivechat;

import codes.monkey.reactivechat.entity.Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;

import java.io.IOException;

public class ChatSocketHandler implements WebSocketHandler {

    private UnicastProcessor<Event> eventPublisher;
    private Flux<String> outputEvents;
    private ObjectMapper mapper;

    public ChatSocketHandler(UnicastProcessor<Event> eventPublisher, Flux<Event> events) {
        this.eventPublisher = eventPublisher;
        this.mapper = new ObjectMapper();
        this.outputEvents = Flux.from(events).map(this::toJSON);
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        WebSocketMessageSubscriber subscriber = new WebSocketMessageSubscriber(eventPublisher);
        return session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .map(this::toEvent)
                .doOnNext(subscriber::onNext)
                .doOnError(subscriber::onError)
                .zipWith(session.send(outputEvents.map(session::textMessage)))
                .then();
    }

    private Event toEvent(String json) {
        try {
            return mapper.readValue(json, Event.class);
        } catch (IOException e) {
            throw new RuntimeException("Invalid JSON:" + json, e);
        }
    }

    private String toJSON(Event event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static class WebSocketMessageSubscriber {
        private UnicastProcessor<Event> eventPublisher;

        public WebSocketMessageSubscriber(UnicastProcessor<Event> eventPublisher) {
            this.eventPublisher = eventPublisher;
        }

        public void onNext(Event event) {

            eventPublisher.onNext(event);
            event.handle(eventPublisher);
        }

        public void onError(Throwable error) {
            //TODO log error
            error.printStackTrace();
        }
    }
}
