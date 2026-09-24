package com.quant.bot;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bots")
public class BotController {

    private final BotService service;

    public BotController(BotService service) {
        this.service = service;
    }

    @GetMapping
    public List<StrategyBot> list() {
        return service.list();
    }

    @PostMapping
    public StrategyBot create(@Valid @RequestBody BotRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public StrategyBot update(@PathVariable long id, @Valid @RequestBody BotRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    /** Evaluate now. {@code dryRun=true} only reports the signal; otherwise orders may be placed. */
    @PostMapping("/{id}/run")
    public BotRunResult run(@PathVariable long id, @RequestParam(defaultValue = "true") boolean dryRun) {
        return service.run(id, dryRun);
    }
}
