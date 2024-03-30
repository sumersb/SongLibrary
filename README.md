# Song Library

This project consists of me trying to minimize latency and delay for a song library, the user has the capability to upload albums, get albums, and like/dislike albums
The documentation for the API is listed at https://app.swaggerhub.com/apis/IGORTON/AlbumStore/1.2#

### This project consists of 3 parts, 
* Client
* Post/Get Album Servlet
* Post/Get (Sentiment) Servlet 
* Consumer

### It also uses these features on AWS:
* MySQL database
* EC2 instances
* Load Balancer
* RabbitMQ
* Redis Cache


#### Client
The client takes in 4 parameters 
* Number of Threads per Group
* Number of Groups
* Delay (s) between the start of each Group
* URL To the Load Balancer

Through these 4 parameters, we initiate a bunch of threads with each thread Posting an album, then sending 2 likes and 1 dislike to the server 1000 times, after the first 
thread group has completed we initiate 3 Threads which repeatedly submit Get Sentiment Requests to retrieve the number of likes and dislikes an album has that has already
been posted to the server. We set out to optimize the Throughput of these Sentiment Query Requests.

#### Album Servlet
This servlet hosted on an AWS EC2 Instances handles Post/Get Album requests and creates an entry in an AWS SQL database with the information of the album set to 0 likes and dislikes
at the same time after the album is posted to the database then a key-value pair with the AlbumID: 0 likes, dislikes are written into a Redis Cache.

#### Sentiment Servlet
This servlet hosted on an AWS EC2 Instance is responsible for managing the likes and dislikes of an album, when a like is passed into a RabbitMQ channel, the consumer
will handle the rest of the processing, if the likes/dislikes are queried in a get request then first it will check the Redis Cache, and if not it will check the SQL database and write
into the Redis Cache

#### Consumer
The consumer receives like and dislikes post requests from the RabbitMQ Channel updates the values in the SQL database and also rewrites the value in the Redis Cache


This was done in Microservice styles to prevent failures on any individual part from cascading to other parts.


