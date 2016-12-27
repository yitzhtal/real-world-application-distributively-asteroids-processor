Real-World-Application-Distributively-Asteroids-Processor

Tal Yitzhak - 204260533
Assaf Magrisso - 201247020

Technical Points:

Running the project – 

Running the project requires exporting the worker and the manager code to jars (worker.jar & manager.jar), uploading them to an S3 bucket named Constants.bucketName, make them public (so that anybody can download them).
The project also requires that an AWSCredentials.zip file will be uploaded to S3 with a relevant password Constants. ZipFilePassword.
All the relevant jar libraries are in the maven dependencies, using maven build they will automatically be downloaded.
The local application can run throw the command line using the following command
Java –jar local.jar inputFileName.txt outputFileName n d 
And a last argument that is up to the user`s choice – is it terminate / not.
*We used t2.micro instances  (ami-b73b63a0)

Did you think for more than 2 minutes about security?
Of course. We decided to secure the credentials like it is mentioned at the assignment instructions: "One way of doing that is by compressing the jar files with a password.".
We have a file named AWSCredentials.zip that is protected by a password which is available on S3. Then, If the Manager/Worker needs to perform some action that requires the access key/secret key, they download the file from S3 and with the password they have they can unzip it and extract the information they need. We assume the local application has this under src/resources, as part of his personal files.

- Threads in your application, when is it a good idea? When is it bad? Invest time to think about threads in your application.
In order to answer this question, we`ll first give an explanation of the way the manager handle local applications: We have 2 kinds of threads running inside the manager:
Local Application Handler – this thread is accepting new messages from local applications, and when a new one is coming, it doesn`t handle its request – it just opens up a new thread inside its own thread pool (with a fixed size), and keep answering new local application requests – so it is basically passing the mission of taking care of a local application to another thread – which makes him available to handle other requests. When million users send message to the Manager – he will be able to accept their messages, and the actual treatment is done by the thread pool. Handling a request also means the thread is responsible for opening a unique queue for that specific local application, so he could get the summary file ASAP when the workers are done analyzing his NASA relevant data. The fixed size thread pool represents the amount of locals that the manager can take care of. 
Workers Handler – this thread is accepting new messages from workers when they are done with their work. It is very similar to the Local Application Handler – it`s responsibility is to get messages from workers and to pass that task to another thread in its own thread pool (also, fixed sized one).  If a lot of analyzed data is sent from the workers side – it still gives attention to each message, which is processed and calculated by an available thread from the thread pool.
Once all tasks of a specific local application is done – we immediately (by the same thread that handled the last Atomic Task that was related to the tasks that was relevant to that specific local application) handle it and send a response back to the local application – so it can get the response ASAP, and we can clear the data structures.
- Did you think about scalability? Will your program work properly when 1 million clients connected at the same time? How about 2 million? 1 billion? Scalability is very important aspect of the system, be sure it is scalable!
Yes, indeed. While working on the project, we set up a few parameters that helps us develop the whole system in a much scalability way:
- We developed a NASA Cache of API Keys, which in case of overloading of messages by the workers, we can handle that issue and get the workers  send REST API requests on another key.
We did this because while working, we got this message after the workers have worked quite a time…
OVER_RATE_LIMIT
You have exceeded your rate limit. Try again later or contact us at https://api.nasa.gov/contact/ for assistance
So we wanted to make sure that if that happens again our system could prevent that.
- We have special constants for our 2 thread pools:
Constants.LocalApplicationHandlerFixedSizeThreadPool
Constants.WorkersHandlerFixedSizeThreadPool
This constants represent the amount of locals/workers that each of the thread pools can handle.
We must mention again, that our system allows/accepts each worker/local that sends a message (we have a special thread for that, like mentioned before). So this constants can be used to define this restriction.
-NASA Web Site`s connectivity – if, for some reason, NASA`s website will crush, we don`t want a million disappointed users (the workers will crush), so we decided to get the status code of the http request – and we are letting a constant number named 
Constants. amountOfAttempsToReconnectToNasa that represents, like it`s name – the amount of times the workers should try get the http request again from NASA.

- What about persistence? What if a node dies? Have you taken care of all possible outcomes in the system? Think of more possible issues that might arise from failures. What did you do to solve it? What about broken communications? Be sure to handle all fail-cases!
- We have a queue named "workersListener" for the workers to listen to new tasks. Once a worker got a new task to handle, it doesn`t delete the message from the queue until it`s done with the task – meaning if he dies, the task will still be visible in the queue for other workers to handle.


- Do you understand how the system works? Do a full run using pen and paper, draw the different parts and the communication that happens between them.
A README.jpg is added.

Did you manage the termination process? Be sure all is closed once requested
The termination process is as follows:
- The local application is sending a "terminate" as an argument running from the command line.
-The program sends LocalApplicationMessage with isTerminate = true.
-The Manager receives the local application request, devide his task to smaller tasks, and pass them to the workers to work on.
-The workers are working on the tasks, and when the last worker is done – the manager, with the AtomicTaskTracker recognize that the whole task for this local application is done, and decides on packing this specific local with the result, and at this moment – it recognizes that it is the last local application that he needs to handle (isTerminated = true), therefore he sends all the workers a TerminationMessage, and call shutDownAllSystem() function which terminate all threads (all threads are inside while(!terminated) loop).
After each worker is done with his task, he is getting his Termination Message and stop running.
Finally, shutDownAllSystem is shuting down all instances by tag (worker & manager). 


Did you take in mind the system limitations that we are using? Be sure to use it to its fullest!
- A NASA Cache of API Keys, if the api-key is blocked after too many get requests.
-We limit the amount of instances to be created by the manager to 19 (Constants.AmountOfInstancesRestrictionOnManager = 19), this number is because the limitation that we have with our AWS Account.
- The process of creating workers by the LocalMsgHandler is fully synchronized, meaning that in case that 2 local applications are starting the system, trying to create workers – is fully covered.


Are all your workers working hard? Or some are slacking? Why?
There are a few parameters that makes the worker "work hard" enough:
- The number of asteroids he has to process on the given dates that he gets from the manager as a task.
- When the system begin, some workers are initializing and running before other workers, so they might start working early then others and therefore work more then others.
- We notices cases in which the tasks was done so fast that some workers didn`t even analyzed (the other ones that initialized before them, finished very quickly).
-If the system is "stable" (meaning all workers are currently running), the tasks will be spread between them in a more fair way then mentioned before.


-Lastly, are you sure you understand what distributed means? Is there anything in your system awaiting another?
A distributed system is a model in which components located on networked computers communicate and coordinate their actions by passing messages.
We have elements in our system that awaits another:
- the local application waits for the manager to finish analyzing his request.
-the manager waits for the workers to finish working on all the requests, and also for the termination message to shut down the whole system.
-the workers are waiting for results from NASA`S website, and also for new tasks to analyze from the manager.

