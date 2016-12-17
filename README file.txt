Real-World-Application-Distributively-Asteroids-Processor
By: 
Tal Yitzhak - 204260533
Assaf Magrisso - 201247020




- Did you think for more than 2 minutes about security? Do not send your credentials in plain text!


- Did you think about scalability? Will your program work properly when 1 million clients connected at the same time? How about 2 million? 1 billion? Scalability is very important aspect of the system, be sure it is scalable!


- What about persistence? What if a node dies? Have you taken care of all possible outcomes in the system? Think of more possible issues that might arise from failures. What did you do to solve it? What about broken communications? Be sure to handle all fail-cases!


- Threads in your application, when is it a good idea? When is it bad? Invest time to think about threads in your application!


- Did you run more than one client at the same time? Be sure they work properly, and finish properly, and your results are correct.


- Do you understand how the system works? Do a full run using pen and paper, draw the different parts and the communication that happens between them.


- Did you manage the termination process? Be sure all is closed once requested!


- Did you take in mind the system limitations that we are using? Be sure to use it to its fullest!
 

- Are all your workers working hard? Or some are slacking? Why?


-Lastly, are you sure you understand what distributed means? Is there anything in your system awaiting another? 