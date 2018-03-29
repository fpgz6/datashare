package org.icij.datashare.text.indexing.elasticsearch;

import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.icij.datashare.Entity;
import org.icij.datashare.PropertiesProvider;
import org.icij.datashare.test.ElasticsearchRule;
import org.icij.datashare.text.Document;
import org.icij.datashare.text.Language;
import org.icij.datashare.text.NamedEntity;
import org.icij.datashare.text.nlp.Pipeline;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.fest.assertions.Assertions.assertThat;
import static org.icij.datashare.test.ElasticsearchRule.TEST_INDEX;
import static org.icij.datashare.text.Document.Status.DONE;
import static org.icij.datashare.text.Document.Status.INDEXED;
import static org.icij.datashare.text.NamedEntity.Category.ORGANIZATION;
import static org.icij.datashare.text.NamedEntity.Category.PERSON;
import static org.icij.datashare.text.nlp.Pipeline.Type.CORENLP;
import static org.icij.datashare.text.nlp.Pipeline.Type.IXAPIPE;
import static org.icij.datashare.text.nlp.Pipeline.Type.OPENNLP;

public class ElasticsearchIndexerTest {
    @ClassRule
    public static ElasticsearchRule es = new ElasticsearchRule();
    private ElasticsearchIndexer indexer = new ElasticsearchIndexer(es.client, new PropertiesProvider()).withRefresh(IMMEDIATE);

    @After
    public void tearDown() throws Exception {
        es.removeAll();
    }

    @Test
    public void test_get_unknown_document() throws Exception {
        Document doc = indexer.get("unknown");
        assertThat(doc).isNull();
    }

    @Test
    public void test_bulk_add() throws IOException {
        Document doc = new org.icij.datashare.text.Document(Paths.get("doc.txt"), "content",
                Language.FRENCH, Charset.defaultCharset(), "application/pdf", new HashMap<>(), INDEXED);
        indexer.add(doc);
        NamedEntity ne1 = NamedEntity.create(PERSON, "John Doe", 12, "doc.txt", CORENLP, Language.FRENCH);
        NamedEntity ne2 = NamedEntity.create(ORGANIZATION, "AAA", 123, "doc.txt", CORENLP, Language.FRENCH);

        assertThat(indexer.bulkAdd(CORENLP, asList(ne1, ne2), doc)).isTrue();

        assertThat(((Document) indexer.get(doc.getId())).getStatus()).isEqualTo(Document.Status.DONE);
        assertThat(((Document) indexer.get(doc.getId())).getNerTags()).containsOnly(CORENLP);
        assertThat((NamedEntity) indexer.get(ne1.getId(), doc.getId())).isNotNull();
        assertThat((NamedEntity) indexer.get(ne2.getId(), doc.getId())).isNotNull();
    }

    @Test
    public void test_bulk_add_should_add_ner_pipeline_once_and_for_empty_list() throws IOException {
        Document doc = new org.icij.datashare.text.Document(Paths.get("doc.txt"), "content", Language.FRENCH,
                Charset.defaultCharset(), "application/pdf", new HashMap<>(), INDEXED,
                new HashSet<Pipeline.Type>() {{ add(OPENNLP);}});
        indexer.add(doc);

        assertThat(indexer.bulkAdd(OPENNLP, emptyList(), doc)).isTrue();

        GetResponse resp = es.client.get(new GetRequest(TEST_INDEX, "doc", doc.getId())).actionGet();
        assertThat(resp.getSourceAsMap().get("status")).isEqualTo("DONE");
        assertThat((ArrayList<String>) resp.getSourceAsMap().get("nerTags")).containsExactly("OPENNLP");
    }

    @Test
    public void test_bulk_add_for_embedded_doc() throws IOException {
        Document parent = new org.icij.datashare.text.Document(Paths.get("mail.eml"), "content",
                Language.FRENCH, Charset.defaultCharset(), "message/rfc822", new HashMap<>(), INDEXED);
        Document child = new org.icij.datashare.text.Document(Paths.get("mail.eml"), "mail body",
                Language.FRENCH, Charset.defaultCharset(), "text/plain", new HashMap<>(), INDEXED, new HashSet<>(), parent);
        indexer.add(parent);
        indexer.add(child);
        NamedEntity ne1 = NamedEntity.create(PERSON, "Jane Daffodil", 12, parent.getId(), CORENLP, Language.FRENCH);

        assertThat(indexer.bulkAdd(CORENLP, singletonList(ne1), child)).isTrue();

        Document doc = indexer.get(child.getId(), parent.getId());
        assertThat(doc.getNerTags()).containsOnly(CORENLP);
        assertThat(doc.getStatus()).isEqualTo(Document.Status.DONE);
        assertThat((NamedEntity) indexer.get(ne1.getId(), doc.getRootDocument())).isNotNull();
    }

    @Test
    public void test_search_no_results() {
        List<? extends Entity> lst = indexer.search(Document.class).execute().collect(toList());
        assertThat(lst).isEmpty();
    }

    @Test
    public void test_search_with_status() {
        Document doc = new org.icij.datashare.text.Document(Paths.get("doc.txt"), "content", Language.FRENCH,
                Charset.defaultCharset(), "application/pdf", new HashMap<>(), INDEXED);
        indexer.add(doc);

        List<? extends Entity> lst = indexer.search(Document.class).ofStatus(INDEXED).execute().collect(toList());
        assertThat(lst.size()).isEqualTo(1);
        assertThat(indexer.search(Document.class).ofStatus(DONE).execute().collect(toList()).size()).isEqualTo(0);
    }

    @Test
    public void test_search_with_and_without_NLP_tags() {
        Document doc = new org.icij.datashare.text.Document(Paths.get("doc.txt"), "content", Language.FRENCH,
                Charset.defaultCharset(), "application/pdf", new HashMap<>(), DONE, new HashSet<Pipeline.Type>() {{ add(CORENLP); add(OPENNLP);}});
        indexer.add(doc);

        assertThat(indexer.search(Document.class).ofStatus(DONE).without(CORENLP).execute().collect(toList()).size()).isEqualTo(0);
        assertThat(indexer.search(Document.class).ofStatus(DONE).without(CORENLP, OPENNLP).execute().collect(toList()).size()).isEqualTo(0);

        assertThat(indexer.search(Document.class).ofStatus(DONE).without(IXAPIPE).execute().collect(toList()).size()).isEqualTo(1);
        assertThat(indexer.search(Document.class).ofStatus(DONE).with(CORENLP).execute().collect(toList()).size()).isEqualTo(1);
        assertThat(indexer.search(Document.class).ofStatus(DONE).with(CORENLP, OPENNLP).execute().collect(toList()).size()).isEqualTo(1);
        assertThat(indexer.search(Document.class).ofStatus(DONE).with(CORENLP, IXAPIPE).execute().collect(toList()).size()).isEqualTo(1);
    }

    @Test
    public void test_search_with_and_without_NLP_tags_no_tags() {
        Document doc = new org.icij.datashare.text.Document(Paths.get("doc.txt"), "content", Language.FRENCH,
                Charset.defaultCharset(), "application/pdf", new HashMap<>(), INDEXED, new HashSet<>());
        indexer.add(doc);

        assertThat(indexer.search(Document.class).without().execute().collect(toList()).size()).isEqualTo(1);
    }

    @Test
    public void test_search_source_filtering() {
        Document doc = new org.icij.datashare.text.Document(Paths.get("doc_with_parent.txt"), "content", Language.FRENCH,
                Charset.defaultCharset(), "application/pdf", new HashMap<>(), INDEXED, new HashSet<>());
        indexer.add(doc);

        Document actualDoc = (Document) indexer.search(Document.class).withSource("contentType").execute().collect(toList()).get(0);
        assertThat(actualDoc.getContentType()).isEqualTo("application/pdf");
        assertThat(actualDoc.getId()).isEqualTo(doc.getId());
        assertThat(actualDoc.getContent()).isEmpty();
    }

    @Test
    public void test_search_source_false() {
        Document doc = new org.icij.datashare.text.Document(Paths.get("doc_with_parent.txt"), "content", Language.FRENCH,
                Charset.defaultCharset(), "application/pdf", new HashMap<>(), INDEXED, new HashSet<>());
        indexer.add(doc);

        Document actualDoc = (Document) indexer.search(Document.class).withSource(false).execute().collect(toList()).get(0);
        assertThat(actualDoc.getId()).isNotNull();
    }


    public ElasticsearchIndexerTest() throws UnknownHostException {}
}
