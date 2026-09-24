package com.quant.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the React app's index.html for client-side routes when the built frontend is bundled in. */
@Controller
public class SpaForwardingController {

    @GetMapping({"/", "/market", "/backtest", "/trading", "/bots", "/approvals"})
    public String index() {
        return "forward:/index.html";
    }
}
