package com.mojang.authlib.yggdrasil;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.mojang.authlib.Environment;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.HttpAuthenticationService;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.exceptions.MinecraftClientException;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.authlib.yggdrasil.response.ProfileSearchResultsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Proxy;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class YggdrasilGameProfileRepository implements GameProfileRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(YggdrasilGameProfileRepository.class);
    private static final int ENTRIES_PER_PAGE = 2;
    private static final int MAX_FAIL_COUNT = 3;
    private static final int DELAY_BETWEEN_PAGES = 100;
    private static final int DELAY_BETWEEN_FAILURES = 750;

    private final MinecraftClient client;
    private final URL searchPageUrl;

    public YggdrasilGameProfileRepository(final Proxy proxy, final Environment environment) {
        this.client = MinecraftClient.unauthenticated(proxy);
        searchPageUrl = HttpAuthenticationService.constantURL(environment.accountsHost() + "/profiles/minecraft");
    }

    @Override
    public void findProfilesByNames(final String[] names, final ProfileLookupCallback callback) {
        final Set<String> criteria = Arrays.stream(names)
            .filter(name -> !Strings.isNullOrEmpty(name))
            .collect(Collectors.toSet());

        final int page = 0;
        boolean hasRequested = false; // Paper

        for (final List<String> request : Iterables.partition(criteria, ENTRIES_PER_PAGE)) {
            final List<String> normalizedRequest = request.stream().map(YggdrasilGameProfileRepository::normalizeName).toList();

            int failCount = 0;
            boolean failed;

            do {
                failed = false;

                try {
                    final ProfileSearchResultsResponse response = client.post(searchPageUrl, normalizedRequest, ProfileSearchResultsResponse.class);
                    final List<GameProfile> profiles = response != null ? response.profiles() : List.of();
                    failCount = 0;

                    LOGGER.debug("Page {} returned {} results, parsing", page, profiles.size());

                    final Set<String> received = new HashSet<>(profiles.size());
                    for (final GameProfile profile : profiles) {
                        LOGGER.debug("Successfully looked up profile {}", profile);
                        received.add(normalizeName(profile.getName()));
                        callback.onProfileLookupSucceeded(profile);
                    }

                    for (final String name : request) {
                        if (received.contains(normalizeName(name))) {
                            continue;
                        }
                        LOGGER.debug("Couldn't find profile {}", name);
                        callback.onProfileLookupFailed(name, new ProfileNotFoundException("Server did not find the requested profile"));
                    }
                    // Paper start
                    if (!hasRequested) {
                        hasRequested = true;
                        continue;
                    }
                    // Paper end

                    try {
                        Thread.sleep(DELAY_BETWEEN_PAGES);
                    } catch (final InterruptedException ignored) {
                    }
                } catch (final MinecraftClientException e) {
                    failCount++;

                    if (failCount == MAX_FAIL_COUNT) {
                        for (final String name : request) {
                            LOGGER.debug("Couldn't find profile {} because of a server error", name);
                            callback.onProfileLookupFailed(name, e.toAuthenticationException());
                        }
                    } else {
                        try {
                            Thread.sleep(DELAY_BETWEEN_FAILURES);
                        } catch (final InterruptedException ignored) {
                        }
                        failed = true;
                    }
                }
            } while (failed);
        }
    }

    private static String normalizeName(final String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
