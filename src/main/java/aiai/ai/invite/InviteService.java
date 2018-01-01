package aiai.ai.invite;

import aiai.ai.beans.InviteResult;
import org.springframework.stereotype.Service;

@Service
public class InviteService {

    public InviteResult processInvite(String invite) {
        InviteResult ir = new InviteResult();
        ir.setUsername("user007");
        ir.setToken("42");
        ir.setPassword("42");
        return ir;
    }
}
