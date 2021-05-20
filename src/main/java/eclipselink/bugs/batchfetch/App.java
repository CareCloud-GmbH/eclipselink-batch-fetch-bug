package eclipselink.bugs.batchfetch;

import eclipselink.bugs.batchfetch.entities.Answer;
import eclipselink.bugs.batchfetch.entities.Assessment;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.eclipse.persistence.annotations.BatchFetchType;
import org.eclipse.persistence.config.QueryHints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


public class App {

    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    private static final int MAX_ROUNDS_TO_TRIGGER_BUG = 100;

    private static final int MAX_ASSESSMENTS = 10;
    private static final int MAX_ANSWERS_PER_ASSESSMENT = 10;
    private static final int BATCH_FETCH_SIZE = 3;

    public static void main(String[] args) {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
        // log queries and bind parameters
        System.setProperty("org.slf4j.simpleLogger.log.eclipselink.logging.sql", "debug");

        var emf = Persistence.createEntityManagerFactory("batchfetch");
        var assessmentIds = generateData(emf);
        loadDataAndTriggerBatchFetchIssue(emf, assessmentIds);
        emf.close();
    }

    /**
     * Generates {@link App#MAX_ASSESSMENTS} assessments and for each assessment
     * {@link App#MAX_ANSWERS_PER_ASSESSMENT} answers. No tags for answers are generated as it is
     * only important to be able to trigger the batch fetch query for tags.
     */
    private static Set<Long> generateData(EntityManagerFactory emf) {
        var em = emf.createEntityManager();
        var tx = em.getTransaction();
        tx.begin();
        try {
            var assessmentIds = new HashSet<Long>();
            for (var id = 1L; id <= MAX_ASSESSMENTS; id++) {
                assessmentIds.add(id); // assuming IDs will be generated in H2 starting with 1.
                var assessment = new Assessment("assessment " + id);
                em.persist(assessment);
                for (long id2 = 1; id2 <= MAX_ANSWERS_PER_ASSESSMENT; id2++) {
                    em.persist(new Answer(assessment, "Answer " + id + "." + id2));
                }
            }
            tx.commit();
            return assessmentIds;
        } finally {
            em.clear();
            em.close();
        }
    }

    /**
     * Selects all generated assessments and specifies a batch fetch hint for
     * {@code assessment.answers.tags}. The query result is shuffled to iterate it in random order
     * triggering batch fetches. The second level of batch fetch ({@code answers.tags}) will then
     * fail with {@link IndexOutOfBoundsException}. The whole process is done in
     * {@link App#MAX_ROUNDS_TO_TRIGGER_BUG} rounds, because even a randomized processing order
     * sometimes succeeds.
     */
    private static void loadDataAndTriggerBatchFetchIssue(EntityManagerFactory emf, Collection<Long> assessmentIds) {
        var jpql = "SELECT assessment FROM Assessment assessment WHERE assessment.id IN :ids";
        for (int i = 1; i <= MAX_ROUNDS_TO_TRIGGER_BUG; i++) {
            LOG.info("======= ROUND {} =======", i);
            var em = emf.createEntityManager();
            try {
                var assessments = em.createQuery(jpql, Assessment.class)
                    .setParameter("ids", assessmentIds)
                    .setHint(QueryHints.BATCH_TYPE, BatchFetchType.IN)
                    .setHint(QueryHints.BATCH, "assessment.answers.tags")
                    .setHint(QueryHints.BATCH_SIZE, BATCH_FETCH_SIZE)
                    .getResultList();

                // Making sure to shuffle a fully independent list
                assessments = new ArrayList<>(assessments);

                // Making sure to trigger batch fetch on assessments in a different order than
                // how assessments have been retrieved by EclipseLink. This occationally triggers an
                // IndexOutOfBoundsException within ForeignReferenceMapping.extractResultFromBatchQuery():592.
                // Commenting this line will workaround/hide the issue.
                //
                // In a real world scenario this different order of processing would happen if the
                // list would be indexed by an assessment property into a multimap,
                // e.g. Map<..., List<Assessment>>, and then iterating through that Map.EntrySet and
                // doing work on all the List<Assessment> instances.
                Collections.shuffle(assessments);

                for (var assessment : assessments) {
                    try {
                        // Trigger batch fetch query for Assessment.answers
                        for (var answer : assessment.getAnswers()) {
                            // Trigger batch fetch query for Assessment.answers.tags
                            // This fails with IndexOutOfBoundsException because
                            // BatchFetchPolicy.dataResults does not contain a Record for the
                            // current answer we are calling getTags().size() on. An index search
                            // then returns -1 which finally causes the exception.
                            answer.getTags().size();
                        }
                        LOG.info("Processed {}", assessment);
                    } catch (IndexOutOfBoundsException e) {
                        LOG.error("Processing failed for {}", assessment);
                        throw e;
                    }
                }
            } finally {
                em.clear();
                em.close();
            }
        }
    }
}
