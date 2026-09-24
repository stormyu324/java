package com.quant.trading;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Orders held for the owner's confirmation. Confirming requires re-entering the login password. */
@RestController
@RequestMapping("/api/trading/approvals")
public class ApprovalController {

    private final TradingService trading;
    private final Reauthenticator reauth;

    public ApprovalController(TradingService trading, Reauthenticator reauth) {
        this.trading = trading;
        this.reauth = reauth;
    }

    @GetMapping
    public List<PendingOrder> list(@RequestParam(defaultValue = "false") boolean all) {
        return all ? trading.recentApprovals() : trading.awaitingApproval();
    }

    @PostMapping("/{id}/approve")
    public OrderOutcome approve(@PathVariable long id, @RequestBody ConfirmRequest body) {
        reauth.verify(body.password());
        return trading.approve(id);
    }

    @PostMapping("/{id}/reject")
    public PendingOrder reject(@PathVariable long id) {
        return trading.reject(id);
    }

    public record ConfirmRequest(String password) {
    }
}
