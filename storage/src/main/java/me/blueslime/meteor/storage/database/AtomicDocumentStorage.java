package me.blueslime.meteor.storage.database;

import me.blueslime.meteor.storage.interfaces.StorageObject;
import me.blueslime.meteor.storage.query.StorageQuery;

import java.util.Optional;

/**
 * Optional capability for single-document atomic operations.
 *
 * <p>This interface DOES NOT provide multi-document transactions. Its methods
 * are usable with a standalone MongoDB server. A caller must design financial
 * invariants so that each protected invariant lives in ONE document, or use a
 * truly transactional backend for invariants spanning documents.</p>
 *
 * <p>Unlike a blind saveOrUpdateSync(), the conditional update checks its
 * predicates and applies the update atomically on the server.</p>
 */
public interface AtomicDocumentStorage {

    /**
     * Inserts exactly one entity if its _id is not already present.
     * Returns false only if that _id already exists. Other uniqueness
     * conflicts and storage failures propagate as exceptions.
     */
    boolean insertIfAbsentSync(StorageObject entity);

    /**
     * Modifies one existing document, provided all conditions still match.
     * The identifier is always included in the predicate. Never upserts.
     * An empty Optional means the document was missing or preconditions failed.
     */
    <T extends StorageObject> Optional<T> updateIfSync(
            Class<T> type,
            String identifier,
            StorageQuery preconditions,
            AtomicMutation mutation
    );
}
