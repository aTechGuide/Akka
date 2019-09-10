# Akka Basics

## How Akka Works
- Akka has a thread pool that it shares with Actors (Actors are passive data structures they need thread to run) 
- An Actor internally is composed of following components
  - Message Handler => Which we define in receive method in Actor classes
  - Message Queue (Mail Box) => Which Akka uses to store the messages that we send to Actors 
- Akka schedules the actor for execution on threads of pool

## Communication
### Sending a message
- Message is enqueued in actor's mailbox in a thread safe manner (done by Akka behind the scenes)

### Processing a message
- Akka schedules a thread to run the actor
- Thread will take control of that particular Actor 
  - It will start dequeuing the messages from Actors mailbox
  - For every message, thread will invoke the message handler (Actor might change its state as a result Or send messages to other actors)
  - The message is then discarded
- At some point, Akka thread scheduler decides to unschedule this Actor for execution
  - Thread will release control of this actor 

## Guarantees
- Only one thread operates on an actor at a time, making actors effectively `Single Threaded`
- We do NOT need any synchronization (NO Locks Needed) as only one thread has access to actors internal state at a time
- Processing messages is atomic as thread may never release the actor in middle of processing messages
- Akka gives `At most ONCE Message Delivery Guarantee` 
  - Receiving actor will receive the message at most once (No Duplicates)
  - For any `sender-receiver` pair the message `order is maintained` 
    - e.g. If Alice sends bob two messages, A and then B, Bob will always receive message A and then B.
      - But if Bob receives messages from other actors also then those messages will be intermingled between A and B
  
