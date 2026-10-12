package com.paymentledger.ledger.infrastructure;

import com.paymentledger.ledger.domain.DuplicateEntryException;
import com.paymentledger.ledger.domain.JournalEntry;
import com.paymentledger.ledger.domain.JournalEntryRepository;
import com.paymentledger.ledger.domain.Posting;
import java.sql.Timestamp;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcJournalEntryRepository implements JournalEntryRepository {

  private final JdbcClient jdbc;

  JdbcJournalEntryRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void save(JournalEntry entry) {
    try {
      jdbc.sql(
              """
              insert into journal_entries (id, reference_type, reference_id, created_at)
              values (?, ?, ?, ?)
              """)
          .params(
              entry.id(),
              entry.reference().type(),
              entry.reference().id(),
              Timestamp.from(entry.createdAt()))
          .update();
    } catch (DuplicateKeyException e) {
      throw new DuplicateEntryException(entry.reference());
    }
    for (Posting posting : entry.postings()) {
      jdbc.sql(
              """
              insert into postings (entry_id, account_id, direction, amount_minor, currency)
              values (?, ?, ?, ?, ?)
              """)
          .params(
              entry.id(),
              posting.accountId(),
              posting.direction().name(),
              posting.amount().minorUnits(),
              posting.amount().currency().getCurrencyCode())
          .update();
    }
  }
}
