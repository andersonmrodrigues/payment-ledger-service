package com.paymentledger.outbox.web;

import com.paymentledger.outbox.application.OutboxReplayService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/outbox")
class OutboxAdminController {

  record ReplayResponse(int requeued) {}

  private final OutboxReplayService replay;

  OutboxAdminController(OutboxReplayService replay) {
    this.replay = replay;
  }

  @PostMapping("/replay")
  ReplayResponse replay(@RequestBody ReplayRequest request) {
    if (request.byIds()) {
      return new ReplayResponse(replay.replay(request.eventIds()));
    }
    if (request.byWindow() && request.from().isBefore(request.to())) {
      return new ReplayResponse(replay.replayCreatedBetween(request.from(), request.to()));
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "Send either eventIds, or from and to with from before to");
  }
}
