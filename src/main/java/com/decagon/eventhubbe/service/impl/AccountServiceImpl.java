package com.decagon.eventhubbe.service.impl;import com.decagon.eventhubbe.config.AccountPaymentOveridden;import com.decagon.eventhubbe.domain.entities.Account;import com.decagon.eventhubbe.domain.entities.AppUser;import com.decagon.eventhubbe.domain.entities.Banks;import com.decagon.eventhubbe.domain.repository.AccountRepository;import com.decagon.eventhubbe.domain.repository.AppUserRepository;import com.decagon.eventhubbe.domain.repository.BankRepository;import com.decagon.eventhubbe.dto.BankData;import com.decagon.eventhubbe.dto.RequestAccountDTO;import com.decagon.eventhubbe.dto.request.SubAccountRequest;import com.decagon.eventhubbe.dto.response.BanksRepo;import com.decagon.eventhubbe.exception.AppUserNotFoundException;import com.decagon.eventhubbe.service.AccountService;import com.decagon.eventhubbe.utils.PaymentUtils;import com.decagon.eventhubbe.utils.UserUtils;import com.fasterxml.jackson.core.type.TypeReference;import com.fasterxml.jackson.databind.ObjectMapper;import lombok.*;import lombok.extern.slf4j.Slf4j;import org.apache.tomcat.util.json.JSONParser;import org.apache.tomcat.util.json.ParseException;import org.modelmapper.ModelMapper;import org.springframework.beans.factory.annotation.Value;import org.springframework.http.*;import org.springframework.stereotype.Service;import org.springframework.util.LinkedMultiValueMap;import org.springframework.util.MultiValueMap;import org.springframework.web.client.RestTemplate;import org.springframework.web.util.UriComponentsBuilder;import java.net.URI;import java.util.*;@Slf4j@Service@RequiredArgsConstructorpublic class AccountServiceImpl implements AccountService {    private final BankRepository bankRepository;    @Value("${payment.key}")    private static String SECRETE_KEY;    private final AccountRepository accountRepository;    private final AppUserRepository appUserRepository;    private final RestTemplate restTemplate;    private final HttpHeaders headers;    private final ModelMapper modelMapper;    /****     * GET ALL BANKS CODE     * PAY STACK     *     * ***/    @Override    public List<BankData> getBankApiCodeDetails() throws  ParseException {        PaymentUtils paymentUtils = new PaymentUtils();        /**** SET PAY STACK KEY  ***/        headers.setBearerAuth(SECRETE_KEY);        headers.set("Cache-Control", "no-cache");        headers.setContentType(MediaType.APPLICATION_JSON);        /**** CREATE URL  ***/        RequestEntity<?> requestEntity = new RequestEntity<>(headers, HttpMethod.GET, URI.create(paymentUtils.getBANK_URL()));        ResponseEntity<String> responseEntity = restTemplate.exchange(requestEntity,String.class);        List<BankData> banksResponseList  = new ObjectMapper().convertValue(new JSONParser(responseEntity.getBody()).object().get("data"), new TypeReference<List<BankData>>() {        });        log.info("BankApi {}",banksResponseList);        if(bankRepository.findAll().size()<1) {            banksResponseList.stream().forEach(                    (i -> {                        bankRepository.save(Banks.builder()                                .bankCode(i.getCode())                                .bankName(i.getName())                                .build());                    }));        }        return banksResponseList;    }    @Override    public RequestAccountDTO saveAccount(RequestAccountDTO requestAccountDTO) {       AppUser user = appUserRepository.findByEmail("chiorlujack@gmail.com").orElseThrow(()->         {             throw new AppUserNotFoundException(UserUtils.getUserEmailFromContext());         });         Banks banks = bankRepository.findAllByCode(requestAccountDTO.getBankName());        Account account = Account.builder()                .accountName(requestAccountDTO.getAccountName())                .bankName(requestAccountDTO.getBankName())                .accountNumber(requestAccountDTO.getAccountNumber())                .appUser(user)                .build();                SubAccountRequest request = new SubAccountRequest(                "EVENT COMPANY",                banks.getBankCode(),                requestAccountDTO.getAccountNumber(),                "1");        String subaccount_code = subbAccount(headers,request).getData().getSubaccount_code();        account.setSubaccount_code(subaccount_code);        return modelMapper.map(accountRepository.save(account),RequestAccountDTO.class);    }    /*** GET BANK AND SEND     * @return     */    public List<Account> getAccount(){        return accountRepository.findAll();    }    @Override    public List<?> getBankCodeAndSend(String bankName, String accountNumber) {        Banks getCode = bankRepository.findAllByCode(bankName);        log.info("code {}", getCode);        PaymentUtils paymentUtils = new PaymentUtils();        headers.setBearerAuth("sk_test_652cb496246c20a1a51456bdf96c12485b37cf7c");        UriComponentsBuilder builder = UriComponentsBuilder                .fromUriString(paymentUtils.getPAY_STACK_URL_RESOLVE())                .queryParam("account_number", accountNumber)                .queryParam("bank_code", getCode.getBankCode())                .queryParam("currency", "NGN");        String url = builder.toUriString();        headers.setContentType(MediaType.APPLICATION_JSON);        HttpEntity<?> requestEntity = new HttpEntity<>(headers);        ResponseEntity<Object> responseEntity = restTemplate.exchange(url, HttpMethod.GET, requestEntity, Object.class);        Object responseBody = responseEntity.getBody();        List<?> users = Collections.singletonList(responseBody);        return users;    }    public  BanksRepo subbAccount(HttpHeaders headers, SubAccountRequest request) {        String url = "https://api.paystack.co/subaccount";        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();        body.add("business_name", request.getBusiness_name());        body.add("bank_code", request.getBank_code());        body.add("account_number", request.getAccount_number());        body.add("percentage_charge", request.getPercentage_charge());        ResponseEntity<BanksRepo> response = AccountPaymentOveridden.performPostRequest(url, body, BanksRepo.class);        System.out.println(response.getBody().getMessage());        return response.getBody();    }    @Override    public RequestAccountDTO updateAccount(RequestAccountDTO requestAccountDTO,String accountId){        Account userAccount  = accountRepository.findById(accountId).orElseThrow(()-> new RuntimeException("Account not found "));       appUserRepository.findByEmail(UserUtils.getUserEmailFromContext()).orElseThrow(()-> {            throw  new AppUserNotFoundException(UserUtils.getUserEmailFromContext());        });        userAccount.setAccountName(requestAccountDTO.getAccountName());        userAccount.setAccountNumber(requestAccountDTO.getAccountNumber());        userAccount.setBankName(requestAccountDTO.getBankName());        return  modelMapper.map(accountRepository.save(userAccount),RequestAccountDTO.class);    }    public void deleteAccount(String accountId){        appUserRepository.findByEmail(UserUtils.getUserEmailFromContext()).orElseThrow(()-> {            throw  new AppUserNotFoundException(UserUtils.getUserEmailFromContext());        });        Account account = accountRepository.findById(accountId).orElseThrow(()-> {            throw  new RuntimeException("Account Not found");        });        accountRepository.delete(account);    }}