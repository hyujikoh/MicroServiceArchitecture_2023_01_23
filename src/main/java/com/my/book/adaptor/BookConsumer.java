package com.my.book.adaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.my.book.config.KafkaProperties;
import com.my.book.domain.event.StockChanged;
import com.my.book.service.BookService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

// 들어오는 인바운드 어댑터
@Service
public class BookConsumer {

    private final Logger log = LoggerFactory.getLogger(BookConsumer.class);

    private final AtomicBoolean closed = new AtomicBoolean(false);

    // 토픽명
    public static final String TOPIC = "topic_rental";

    private final KafkaProperties kafkaProperties;

    private KafkaConsumer<String, String> kafkaConsumer;

    private final BookService bookService;

    private ExecutorService executorService = Executors.newCachedThreadPool();


    public BookConsumer(KafkaProperties kafkaProperties, BookService rentalService) {
        this.kafkaProperties = kafkaProperties;
        this.bookService = rentalService;
    }

    @PostConstruct
    public void start(){
        log.info("Kafka consumer starting ...");
        this.kafkaConsumer = new KafkaConsumer<>(kafkaProperties.getConsumerProps());
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        kafkaConsumer.subscribe(Collections.singleton(TOPIC));
        log.info("Kafka consumer started");

        executorService.execute(()-> {
                try {
                    while (!closed.get()){
                        ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofSeconds(3));
                        for(ConsumerRecord<String, String> record: records){
                            log.info("Consumed message in {} : {}", TOPIC, record.value());
                            ObjectMapper objectMapper = new ObjectMapper();
                            //(1) 카프카에서 읽은 메시지를 대출 MS 가 보낸 stockchanged 도메인 이벤트로 변환
                            StockChanged stockChanged = objectMapper.readValue(record.value(), StockChanged.class);

                            //(2) 이 도메인 이벤트 정보를 가지고 bookService를 호출해 도서의 재고 상태를 업데이트 한다.
                            bookService.processChangeBookState(stockChanged.getBookId(), stockChanged.getBookStatus()); //(2)
                        }
                    }
                    kafkaConsumer.commitSync();
                }catch (WakeupException e){
                    if(!closed.get()){
                        throw e;
                    }
                }catch (Exception e){
                    log.error(e.getMessage(), e);
                }finally {
                    log.info("kafka consumer close");
                    kafkaConsumer.close();
                }
            }
        );
    }

    public KafkaConsumer<String, String> getKafkaConsumer() {
        return kafkaConsumer;
    }
    public void shutdown() {
        log.info("Shutdown Kafka consumer");
        closed.set(true);
        kafkaConsumer.wakeup();
    }
}
