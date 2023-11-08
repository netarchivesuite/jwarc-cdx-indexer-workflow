package dk.kb.cdx.workflow;

public class WorkerStatus {
    private int completed=0;
    private int errors=0;
        
    public WorkerStatus() {
                
    }

    public void increaseCompleted() {
        completed++;
    }
    
    public void increaseErrors() {
        errors++;
    }

    public int getCompleted() {
        return completed;
    }


    public int getErrors() {
        return errors;
    }
    
    

}
