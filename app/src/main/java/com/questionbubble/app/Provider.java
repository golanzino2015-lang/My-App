package com.questionbubble.app;

/** The AI services the app can use. Each has its own key and model. */
enum Provider {

    OPENAI("openai", "ChatGPT", "OpenAI", "sk-...",
            "https://platform.openai.com/api-keys", "platform.openai.com", "gpt-4o",
            new String[][]{
                    {"gpt-4o", "Balanced. Great with screenshots"},
                    {"gpt-4o-mini", "Fastest and cheapest"},
                    {"gpt-4.1", "Strong reasoning"},
                    {"gpt-5-mini", "Newer, efficient"},
                    {"gpt-5", "Most capable, slower"},
            }),

    CLAUDE("anthropic", "Claude", "Anthropic", "sk-ant-...",
            "https://console.anthropic.com/settings/keys", "console.anthropic.com", "claude-opus-5-5",
            new String[][]{
                    {"claude-opus-5-5", "Most capable for everyday use"},
                    {"claude-sonnet-5-5", "Fast and capable"},
                    {"claude-haiku-5-5", "Fastest and cheapest"},
                    {"claude-fable-5-1", "Anthropic's most capable, slower"},
            }),

    GEMINI("gemini", "Gemini", "Google", "AIza...",
            "https://aistudio.google.com/apikey", "aistudio.google.com", "gemini-2.5-flash",
            new String[][]{
                    {"gemini-2.5-flash", "Fast, great with images"},
                    {"gemini-2.5-pro", "Most capable"},
                    {"gemini-2.5-flash-lite", "Fastest and cheapest"},
            });

    final String id, label, company, keyHint, keyUrl, keySite, defaultModel;
    final String[][] models;

    Provider(String id, String label, String company, String keyHint, String keyUrl, String keySite,
             String defaultModel, String[][] models) {
        this.id = id;
        this.label = label;
        this.company = company;
        this.keyHint = keyHint;
        this.keyUrl = keyUrl;
        this.keySite = keySite;
        this.defaultModel = defaultModel;
        this.models = models;
    }

    static Provider fromId(String id) {
        for (Provider p : values()) if (p.id.equals(id)) return p;
        return OPENAI;
    }
}
