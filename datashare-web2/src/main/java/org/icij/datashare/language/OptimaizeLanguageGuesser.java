package org.icij.datashare.language;

import com.optimaize.langdetect.LanguageDetector;
import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.i18n.LdLocale;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.LanguageProfileReader;
import com.optimaize.langdetect.text.CommonTextObjectFactories;
import com.optimaize.langdetect.text.TextObjectFactory;

import java.io.IOException;

public class OptimaizeLanguageGuesser implements LanguageGuesser {
    private final LanguageDetector languageDetector;

    public OptimaizeLanguageGuesser() throws IOException {
        this.languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                        .withProfiles(new LanguageProfileReader().readAllBuiltIn())
                        .build();
    }

    @Override
    public String guess(String text) {
        TextObjectFactory textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText();
        return languageDetector.detect(textObjectFactory.forText(text)).or(LdLocale.fromString("en")).getLanguage();
    }
}
