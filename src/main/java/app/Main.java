package app;

import com.google.common.collect.Maps;
import org.reactivestreams.Publisher;
import ratpack.form.Form;
import ratpack.rx.RxRatpack;
import ratpack.server.BaseDir;
import ratpack.server.RatpackServer;
import ratpack.server.Service;
import ratpack.server.StartEvent;
import ratpack.websocket.WebSockets;
import rx.subjects.PublishSubject;

import java.util.Map;

public class Main {

  static class StreamContainer implements Service {
    enum StreamType {
      A, B, C;
    }

    private Map<StreamType, PublishSubject<String>> streamStorage = Maps.newHashMap();

    public void onStart(StartEvent event) {
      for (StreamType t : StreamType.values()) {
        streamStorage.put(t, PublishSubject.<String>create());
      }
    }

    public void publish(StreamType type, String message) {
      streamStorage.get(type).onNext(message);
    }

    public Publisher<String> getStream(StreamType t) {
      return RxRatpack.publisher(streamStorage.get(t));
    }
  }

  public static void main(String[] args) throws Exception {
    RatpackServer.start(spec -> spec
      .serverConfig(sbuild -> sbuild
          .baseDir(BaseDir.find())
      )
      .registryOf(rspec -> rspec
        .add(new StreamContainer())
      )
      .handlers(chain -> chain
              .post("message", ctx -> {
                StreamContainer container = ctx.get(StreamContainer.class);
                ctx.parse(Form.class).then(form -> {
                  String type = form.get("type");
                  String message = form.get("message");
                  StreamContainer.StreamType st = StreamContainer.StreamType.valueOf(type);
                  container.publish(st, message);
                  ctx.getResponse().send();
                });
              })
              .get("ws/:type?", ctx -> {
                StreamContainer container = ctx.get(StreamContainer.class);
                StreamContainer.StreamType type = StreamContainer.StreamType.valueOf(ctx.getPathTokens().getOrDefault("type", "A"));
                WebSockets.websocketBroadcast(ctx, container.getStream(type));
              })
              .files(fspec -> fspec
                      .dir("public").indexFiles("index.html")
              )
      )
    );
  }

}
