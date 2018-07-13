package org.icij.datashare.text;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import me.xuender.unidecode.Unidecode;
import org.icij.datashare.Entity;
import org.icij.datashare.function.ThrowingFunction;
import org.icij.datashare.function.ThrowingFunctions;
import org.icij.datashare.text.indexing.IndexId;
import org.icij.datashare.text.indexing.IndexParent;
import org.icij.datashare.text.indexing.IndexType;
import org.icij.datashare.text.nlp.Annotations;
import org.icij.datashare.text.nlp.Pipeline;
import org.icij.datashare.text.nlp.Tag;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.min;
import static org.icij.datashare.function.ThrowingFunctions.removePattFrom;
import static org.icij.datashare.text.NamedEntity.Category.UNKNOWN;
import static org.icij.datashare.text.nlp.NlpStage.NER;
import static org.icij.datashare.text.nlp.NlpStage.POS;


@IndexType("NamedEntity")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class NamedEntity implements Entity {
    private static final long serialVersionUID = 1946532866377498L;

    private String mention;
    private final String mentionNorm;

    @IndexId
    @JsonIgnore
    private final String id;
    private final Category category;
    @IndexParent
    private final String documentId;
    private final int offset;
    private final Pipeline.Type extractor;
    private final Language extractorLanguage;
    private final String partsOfSpeech;

    public enum Category implements Serializable {
        PERSON       ("PERS"),
        ORGANIZATION ("ORG"),
        LOCATION     ("LOC"),
        DATE         ("DATE"),
        MONEY        ("MON"),
        NUMBER       ("NUM"),
        NONE         ("NONE"),
        UNKNOWN      ("UNK");

        private static final long serialVersionUID = -1596432856473673L;

        private final String abbreviation;

        Category(final String abbrev) { abbreviation = abbrev; }

        public String getAbbreviation() { return abbreviation; }

        public static Category parse(String entityCategory) {
            if (entityCategory == null || entityCategory.isEmpty() ||
                entityCategory.trim().equals("0") || entityCategory.trim().equals("O")) {
                return NONE;
            }
            try {
                return valueOf(entityCategory.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                String normEntityCategory = removePattFrom.apply("^I-").apply(entityCategory);
                for (Category cat : values()) {
                    String catAbbreviation = cat.getAbbreviation();
                    if (normEntityCategory.equalsIgnoreCase(catAbbreviation) ||
                        normEntityCategory.equalsIgnoreCase(catAbbreviation.substring(0, min(catAbbreviation.length(), 3))) )
                        return cat;
                }
                return UNKNOWN;
            }
        }

        public static ThrowingFunction<List<String>, List<Category>> parseAll =
                list -> list.stream()
                        .map(Category::parse)
                        .filter((Category cat) -> cat != UNKNOWN)
                        .collect(Collectors.toList());
    }

    public static NamedEntity create(Category cat,
                                     String mention,
                                     int offset,
                                     String doc,
                                     Pipeline.Type extr,
                                     Language extrLang) {
        return new NamedEntity(mention, cat, doc, offset, extr, extrLang, null);
    }

    public static List<NamedEntity> allFrom(String text, Annotations annotations) {
        return annotations.get(NER).stream()
                .map     ( tag -> from(text, tag, annotations) )
                .filter  ( ne -> ne.category != UNKNOWN)
                .collect ( Collectors.toList() );
    }

    public static NamedEntity from(String text, Tag tag, Annotations annotations) {
        Category category = Category.parse(tag.getValue());
        String mention = ThrowingFunctions.removeNewLines.apply(text.substring(tag.getBegin(), tag.getEnd()));
        List<Tag> posTags = annotations.get(POS);
        int posTagIndex = Collections.binarySearch(posTags, tag, Tag.comparator);
        if (posTagIndex > 0) {
            LOGGER.info(posTagIndex + ", " + posTags.get(posTagIndex));
        }
        return NamedEntity.create(
                category,
                mention,
                tag.getBegin(),
                annotations.getDocumentId(),
                annotations.getPipelineType(),
                annotations.getLanguage()
        );
    }

    @JsonCreator
    private NamedEntity(@JsonProperty("mention") String mention,
                        @JsonProperty("category") Category category,
                        @JsonProperty("documentId") String documentId,
                        @JsonProperty("offset") int offset,
                        @JsonProperty("extractor") Pipeline.Type extractor,
                        @JsonProperty("extractorLanguage") Language extractorLanguage,
                        @JsonProperty("partOfSpeech") String partsOfSpeech) {
        if (mention == null || mention.isEmpty()) {
            throw new IllegalArgumentException("Mention is undefined");
        }
        this.category = Optional.ofNullable(category).orElse(UNKNOWN);
        this.mention = mention;
        this.mentionNorm = normalize(mention);
        this.documentId = documentId;
        this.offset = offset;
        this.extractor = extractor;
        this.id = HASHER.hash( String.join("|",
                getDocumentId().toString(),
                String.valueOf(offset),
                getExtractor().toString(),
                mentionNorm
        ));
        this.extractorLanguage = extractorLanguage;
        this.partsOfSpeech = partsOfSpeech;
    }

    @Override
    public String getId() { return id; }
    public String getMention() { return mention; }
    public Category getCategory() { return category; }
    public String getDocumentId() { return documentId; }
    public int getOffset() { return offset; }
    public Pipeline.Type getExtractor() { return extractor; }
    public Language getExtractorLanguage() { return extractorLanguage; }
    public String getPartsOfSpeech() { return partsOfSpeech; }

    @Override
    public String toString() {
        return "NamedEntity{" +
                "mention='" + mention + '\'' +
                ", id='" + id + '\'' +
                ", category=" + category +
                ", offset=" + offset +
                '}';
    }

    @JsonIgnore
    public static String normalize(String unicoded) {
        return Unidecode.decode(unicoded).trim().replaceAll("(\\s+)", " ").toLowerCase();
    }

}
