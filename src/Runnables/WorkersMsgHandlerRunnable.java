package Runnables;

import JsonObjects.AtomicTask;
import MainPackage.Manager;
import MainPackage.mySQS;
import com.amazonaws.services.sqs.model.Message;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class WorkersMsgHandlerRunnable implements Runnable{

    List<Message> result;
    String managerListenerURL;

    public WorkersMsgHandlerRunnable(List<Message> result, String managerListenerURL) {
        this.result = result;
        this.managerListenerURL = managerListenerURL;
    }

    @Override
    public void run() {
        if(!Manager.terminated) {
            AtomicTask AtomicTaskFromWorker = null;
            for(com.amazonaws.services.sqs.model.Message msg : result) {
                AtomicTaskFromWorker = new Gson().fromJson(msg.getBody(), AtomicTask.class);

                //deletes message from queue...
                mySQS.getInstance().deleteMessageFromQueue(managerListenerURL,msg);

                ArrayList<AtomicTask> tasks = Manager.mapLocals.get(AtomicTaskFromWorker.getLocalUUID());

                if(tasks != null) {
                    for (AtomicTask f : tasks) {
                        if(f.getTaskUUID().equals(AtomicTaskFromWorker.getTaskUUID())) {
                            if(!f.getDone()) {
                                System.out.println("Manager (workersHandler) :: " + f.getTaskUUID() + " task is marked as DONE !!!");
                                f.setDone(true);
                                f.setAtomicAnalysisResult(AtomicTaskFromWorker.getAtomicAnalysisResult());
                            } else {
                                System.out.println("Manager (workersHandler) :: " + f.getTaskUUID() + " task is already marked as done.");
                            }
                        }
                    }
                }
            }
        }
    }
}
