package com.happyfarm.frontend.assets

object Css:
  val animatedThreeDots =
    """
    @keyframes blink {
      0%, 20%   { opacity: 0; }
      40%       { opacity: 1; }
      100%      { opacity: 0; }
    }
    .dot {
      animation: blink 1.4s infinite;
      display: inline-block;
      line-height: 1;
    }
    .dot:nth-child(1) { animation-delay: 0.0s; }
    .dot:nth-child(2) { animation-delay: 0.2s; }
    .dot:nth-child(3) { animation-delay: 0.4s; }
    """

  val hideScrollbarStyles =
    """
      .no-scrollbar::-webkit-scrollbar {
        display: none;
      }
      .no-scrollbar {
        -ms-overflow-style: none;
        scrollbar-width: none;
      }
    """

  val containerView =
    "mx-auto my-4 w-full max-w-[500px] h-[650px] flex flex-col border border-gray-198 rounded-2xl bg-white shadow-2xl overflow-hidden"
  val header =
    "h-20 flex flex-col items-center justify-center border-b bg-white flex-shrink-0 relative"
  val title = "text-lg font-bold text-gray-800 truncate max-w-[60%]"

  object RegistrationPage:
    val happyFarmLogoStyle           = "block mx-auto w-1/2"
    val registrationFormWrapper      = "max-w-md mx-auto mt-5 p-6 border border-gray-300 rounded-lg shadow-md"
    val registrationFormTitle        = "text-2xl font-normal text-center mb-6"
    val registrationFormErrorMessage = "text-red-500 text-center"
    val registrationFormSuccessMessage    = "text-green-500 text-center"
    val registrationFormStyle             = "space-y-4"
    val registrationNameLabelStyle        = "block text-sm font-medium text-gray-700"
    val registrationNameInputBoxStyle     = "mt-1 block w-full p-2 border border-gray-300 rounded-md"
    val registrationPasswordLabelStyle    = "block text-sm font-medium text-gray-700"
    val registrationPasswordInputBoxStyle = "mt-1 block w-full p-2 border border-gray-300 rounded-md"
    val registrationTokenLabelStyle       = "block text-sm font-medium text-gray-700"
    val registrationTokenInputBoxStyle    = "mt-1 block w-full p-2 border border-gray-300 rounded-md"
    val registrationButtonWrapper         = "flex justify-center"
    val registrationButtonStyle           = "hover:opacity-75"
    val registrationSignInWrapper         = "flex justify-center"
    val registrationSignInStyle           = "underline text-black-600 cursor-pointer"

  /** ****************************************************************************
    */
  object LoginPage:
    val happyFarmLogoStyle          = "block mx-auto w-1/2"
    val loginFormWrapper            = "max-w-md mx-auto mt-5 p-6 border border-gray-300 rounded-lg shadow-md"
    val loginFormTitle              = "text-2xl font-normal text-center mb-6"
    val loginFormErrorMessage       = "text-red-500 text-center"
    val loginFormStyle              = "space-y-4"
    val loginFormNameLabelStyle     = "block text-sm font-medium text-gray-700"
    val loginFormNameInputBoxStyle  = "mt-1 block w-full p-2 border border-gray-300 rounded-md"
    val loginFormPasswordLabelStyle = "block text-sm font-medium text-gray-700"
    val loginFormPasswordInputBoxStyle = "mt-1 block w-full p-2 border border-gray-300 rounded-md"
    val loginFormSignInWrapper         = "flex justify-center mt-[10px]"
    val loginFormSignInButtonStyle     = "hover:opacity-75"
    val loginFormRegisterWrapper       = "flex justify-center"
    val loginFormRegisterButtonStyle   = "underline text-black-600 cursor-pointer"

  /** ****************************************************************************
    */
  object ChatRoomsOverviewPage:
    val header = "h-16 flex items-center justify-center border-b"
    val scrollableArea =
      "flex-grow overflow-y-auto border border-gray-300 rounded-lg mx-4 my-2"

    val listScrollableArea              = "flex flex-col min-h-full bg-white"
    val listScrollableAreaSpaceFiller   = "flex-grow"
    val listScrollableAreaFooterPadding = "h-20"
    val roomSummaryContainer =
      "flex items-center h-20 px-4 py-2 border-b border-gray-100 cursor-pointer hover:bg-gray-50"
    val roomSummaryAvatar            = "flex-shrink-0"
    val roomSummaryOuter             = "ml-4 flex-grow flex flex-col justify-center min-w-0"
    val roomSummaryInner             = "flex justify-between items-baseline"
    val roomSummaryTitle             = "font-bold text-base truncate"
    val roomSummaryDate              = "text-gray-400 text-xs ml-2 flex-shrink-0"
    val roomSummaryBottomRow         = "flex justify-between items-center"
    val roomSummaryMessagePreview    = "text-gray-500 text-sm truncate pr-4"
    val roomSummaryDraftMessageAlert = "text-rose-600"
    val roomSummaryUnreadBadge =
      "flex items-center justify-center min-w-[20px] h-5 px-1.5 " +
        "bg-red-500 text-white text-[11px] font-bold rounded-full " +
        "shadow-sm ml-2 flex-shrink-0"

    val bottomNavLayout = "flex justify-center items-center gap-8 py-4 border-t border-gray-100 bg-white"
    val navButton       = "flex-shrink-0 focus:outline-none transition-transform active:scale-95"
    val navIconEffect   = "w-[95px] h-auto cursor-pointer object-contain"

  /** ****************************************************************************
    */
  object ProfilePage:
    val profileWrapper             = "flex-grow flex flex-col items-center justify-center p-8 space-y-8"
    val profileInfoContainer       = "flex flex-col items-center space-y-6 animate-in fade-in duration-500"
    val profileInfoWrapper         = "flex flex-col items-center space-y-2"
    val profileInfoNameStyle       = "text-3xl font-bold text-gray-800 tracking-tight"
    val profileInfoJoinedDateStyle = "text-sm text-gray-500"
    val profileButtonWrapper       = "pt-12"
    val profileButtonStyle         = "flex flex-col items-center space-y-2 group"
    val profileButtonIconStyle     = "opacity-70 group-hover:opacity-100 transition-opacity cursor-pointer"
    val profileFriendshipRequestedStyle = "text-emerald-600 font-bold animate-pulse"

  /** ****************************************************************************
    */
  object FriendsPage:
    val scrollableArea = "flex-grow overflow-y-auto mx-4 my-2 no-scrollbar"
    val friendItemContainer =
      "flex items-center h-20 px-4 py-2 border-b border-gray-100 cursor-pointer hover:bg-gray-50 transition-colors"
    val friendInfoOuter           = "ml-4 flex-grow flex flex-col justify-center"
    val friendName                = "font-bold text-lg text-gray-800"
    val friendJoinedDate          = "text-gray-400 text-xs"
    val friendAcceptButtonWrapper = "flex-shrink-0 ml-2"
    val friendAcceptButtonStyle =
      "bg-emerald-500 hover:bg-emerald-600 text-white px-4 py-1.5 rounded-lg text-sm font-bold shadow-sm active:scale-95 transition-all"

    val searchBarWrapper  = "p-4 bg-white border-b border-gray-100"
    val searchBarPosition = "relative flex items-center"
    val searchBarInputBoxStyle =
      "w-full pl-4 pr-10 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500 transition-all text-sm"

    val friendsListWrapper        = "flex flex-col min-h-full bg-white"
    val friendsListNoFriendsStyle = "flex flex-col items-center justify-center mt-20 text-gray-400"
    val friendsListPendingListWrapper =
      "px-4 py-2 text-xs font-bold text-emerald-600 uppercase tracking-wider bg-emerald-50"
    val friendsListExistingListWrapper =
      "px-4 py-2 text-xs font-bold text-gray-500 uppercase tracking-wider bg-gray-50"

  /** ****************************************************************************
    */
  object ChatPage:
    val scrollableHistory = "flex-grow overflow-y-auto px-4 py-4 bg-white flex flex-col no-scrollbar"
    val messageListUnreadSeparatorPosition = "flex items-center my-6 px-4"
    val messageListUnreadSeparatorLine     = "flex-grow border-t-2 border-emerald-100"
    val messageListUnreadSeparatorText =
      "mx-4 text-emerald-600 text-[10px] font-bold uppercase tracking-widest whitespace-nowrap"
    val messageListDateSeparatorPosition = "flex justify-center my-4"
    val messageListDateSeparatorStyle    = "bg-gray-100 text-gray-500 text-xs px-3 py-1 rounded-full"
    val messageListUnreadMessageNotificationStyle =
      "absolute top-24 left-1/2 -translate-x-1/2 bg-emerald-500 text-white px-4 py-2 rounded-full shadow-lg cursor-pointer text-sm"
    val messageBoxSelfPosition     = "flex justify-end mb-4"
    val messageBoxWrapperStyle     = "flex items-end gap-3 max-w-[85%] flex-row-reverse"
    val messageBoxShapeStyle       = "max-w-[100%] p-3 rounded-2xl shadow-sm"
    val messageBoxSelfColorStyle   = "bg-cyan-50 text-gray-800 rounded-tr-none"
    val messageBoxPeerColorStyle   = "bg-emerald-50 text-gray-800 rounded-tl-none"
    val messageBoxPeerPosition     = "flex justify-start mb-4"
    val messageBoxUserNameDisplay  = "text-xs font-bold text-gray-500 mb-1 opacity-75"
    val messageBoxContentDisplay   = "text-[15px] leading-relaxed whitespace-normal break-words"
    val messageBoxTimestampDisplay = "text-[10px] text-gray-400 mt-1 text-right font-mono"
    val messageBoxMessageStillSendingSpinner =
      "flex-shrink-0 animate-spin h-3 w-3 border-2 border-gray-300 border-t-cyan-500 rounded-full"
    val messageBoxFailedToSentExclamationMark   = "flex-shrink-0 text-red-500 font-bold"
    val messageBoxPeerTypingSignalStyle         = "flex gap-1 py-1 px-1 items-center"
    val messageBoxPeerTypingSignalFirstDotStyle = "w-1 h-1 bg-gray-400 rounded-full animate-bounce"
    val messageBoxPeerTypingSignalSecondDotStyle =
      "w-1 h-1 bg-gray-400 rounded-full animate-bounce [animation-delay:0.2s]"
    val messageBoxPeerTypingSignalThirdDotStyle =
      "w-1 h-1 bg-gray-400 rounded-full animate-bounce [animation-delay:0.4s]"

    val resendBoxWrapper = "absolute inset-0 z-50 flex items-center justify-center bg-black/20 p-6"
    val resendBoxContainer =
      "w-full max-w-[280px] bg-white rounded-3xl overflow-hidden shadow-2xl border border-gray-100"
    val resendBoxResendButton =
      "w-full p-4 text-emerald-600 font-bold border-b border-gray-100 active:bg-gray-50"
    val resendBoxCancelButton = "w-full p-4 text-gray-400 font-medium active:bg-gray-50"

    val inputContainerStyle          = "p-4 border-t border-gray-100 bg-white flex-shrink-0"
    val inputContainerAlignmentStyle = "flex items-end gap-2"
    val textInputStyle =
      "flex-grow resize-none rounded-xl border border-gray-200 px-4 py-3 placeholder-gray-400 text-gray-800 outline-none"
    val sendButtonStyle =
      "bg-emerald-500 hover:bg-emerald-600 text-white w-12 h-12 rounded-full flex items-center justify-center shadow-md active:scale-90 transition-transform cursor-pointer border-none"
    val footerPadding = "h-4"

object Html:
  object Main:
    val loading    = "Loading"
    val loginAgain = "We're having trouble recognizing your saved session. Please sign in again to continue."
    val failedToConnectToServer = "Failed to connect to server. Please try login again"

  object RegistrationPage:
    val registration     = "Register HappyFarm account"
    val name             = "Name"
    val password         = "Password"
    val token            = "Registration Token"
    val navigateToSignIn = "Go to sign in"

  object LoginPage:
    val name               = "Name"
    val password           = "Password"
    val navigateToRegister = "Register"

  object ChatRoomsOverviewPage:
    val loading      = "Loading Chats"
    val profileAlt   = "Profile"
    val addFriendAlt = "Friends"

  object ChatPage:
    val loading                = "Loading Messages"
    val typeMessagePlaceHolder = "Type your message..."
    val resendButton           = "Resend Message"
    val cancelButton           = "Cancel"

  object ProfilePage:
    val loading             = "Loading Profile"
    val friendShipRequested = "Friendship requested"

  object FriendsPage:
    val loading           = "Loading Friends"
    val title             = "Friends"
    val noFriends         = "You haven't added any friends yet."
    val search            = "Find a friend by name..."
    val pendingListTitle  = "Pending Requests"
    val existingListTitle = "Friends"
