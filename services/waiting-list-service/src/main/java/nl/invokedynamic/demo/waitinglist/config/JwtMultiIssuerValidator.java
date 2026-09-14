package nl.invokedynamic.demo.waitinglist.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.Assert;

import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Validates that the JWT {@code iss} claim matches one of the configured trusted issuer URIs.
 */
public class JwtMultiIssuerValidator implements OAuth2TokenValidator<Jwt> {

    private final Collection<String> allowedIssuers;

    public JwtMultiIssuerValidator(Collection<String> allowedIssuers) {
        Assert.notEmpty(allowedIssuers, "allowedIssuers must not be empty");
        this.allowedIssuers = List.copyOf(allowedIssuers);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        URL issuer = jwt.getIssuer();
        String issuerString = issuer != null ? issuer.toString() : null;
        if (issuerString != null && allowedIssuers.contains(issuerString)) {
            return OAuth2TokenValidatorResult.success();
        }

        OAuth2Error error = new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN,
                "The iss claim is not valid. Expected one of: " + allowedIssuers,
                "https://tools.ietf.org/html/rfc6750#section-3.1"
        );
        return OAuth2TokenValidatorResult.failure(error);
    }

    public Collection<String> getAllowedIssuers() {
        return Collections.unmodifiableCollection(allowedIssuers);
    }
}
