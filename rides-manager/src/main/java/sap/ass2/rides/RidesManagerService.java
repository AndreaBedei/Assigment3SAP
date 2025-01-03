package sap.ass2.rides;

import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import sap.ass2.rides.application.CustomKafkaListener;
import sap.ass2.rides.application.EbikesManagerRemoteAPI;
import sap.ass2.rides.application.EventCollector;
import sap.ass2.rides.application.KafkaConsumerFactory;
import sap.ass2.rides.application.KafkaProducerFactory;
import sap.ass2.rides.application.RidesManagerAPI;
import sap.ass2.rides.application.RidesManagerImpl;
import sap.ass2.rides.application.UsersManagerRemoteAPI;
import sap.ass2.rides.infrastructure.RidesManagerController;

public class RidesManagerService {
    private RidesManagerAPI ridesManager;
    private RidesManagerController ridesController;
    private URL localAddress;
    private CustomKafkaListener listener;
    private EventCollector eventCollector;
    private static Logger logger = Logger.getLogger("[RIDES MANAGER]");

    public RidesManagerService(URL localAddress, UsersManagerRemoteAPI usersManager, EbikesManagerRemoteAPI ebikesManager) {
        this.localAddress = localAddress;

        this.listener = new CustomKafkaListener(KafkaConsumerFactory.defaultConsumer(), "ride-events", "user-events", "ebike-events");
        this.eventCollector = new EventCollector(this.listener);
        this.ridesManager = new RidesManagerImpl(this.eventCollector, KafkaProducerFactory.kafkaProducer(), this.listener);

        var futureForUsers = usersManager.getAllUsers();
        var futureForEbikes = ebikesManager.getAllEbikes();

        Future.all(futureForUsers, futureForEbikes).onSuccess((cf) -> {
            List<JsonArray> results = cf.list();
            eventCollector.init(results.get(0), results.get(1));
        });

        //this.listener.onEach(e -> logger.log(Level.INFO, "Received event: " + e));
    }

    public void launch(){
        // Starts the ride controller (so that it can start the RidesExecutionVerticle).
        this.ridesController = new RidesManagerController(this.localAddress.getPort());
        this.ridesController.init(this.ridesManager, this.listener);
        CompletableFuture.runAsync(this.listener);
    }
}
