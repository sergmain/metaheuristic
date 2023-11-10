package ai.metaheuristic.ai.dispatcher.lucene;

/**
 * User: SMaslyukov
 * Date: 01.06.2007
 * Time: 17:23:04
 */
public class PortalSearchException extends RuntimeException {

    public PortalSearchException(){
        super();
    }

    public PortalSearchException(String s){
        super(s);
    }

    public PortalSearchException(String s, Throwable cause){
        super(s, cause);
    }
}
